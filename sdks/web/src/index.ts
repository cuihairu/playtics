export type PlayticsOptions = {
  apiKey: string;
  endpoint: string; // e.g. https://ingest.playtics.io
  projectId: string;
  deviceId?: string;
  flushIntervalMs?: number; // default 5000
  maxBatch?: number;        // default 50
  maxQueueBytes?: number;   // default 512_000
  sessionGapMs?: number;    // default 30*60*1000
  debug?: boolean;
};

export type EventProps = Record<string, any>;

type Event = {
  event_id: string;
  event_name: string;
  project_id: string;
  user_id?: string | null;
  device_id: string;
  session_id?: string | null;
  ts_client: number; // epoch ms
  platform?: string | null;
  app_version?: string | null;
  country?: string | null;
  props?: EventProps | null;
};

function nowMs() { return Date.now(); }

// Simple UUIDv7 generator (millisecond timestamp + randomness). Not cryptographically strong.
function uuidv7(): string {
  const t = BigInt(nowMs());
  // timestamp (48 bits ms) -> place in first 12 hex chars
  const tsHex = t.toString(16).padStart(12, '0').slice(-12);
  // random 64 bits
  const r1 = crypto.getRandomValues(new Uint32Array(2));
  const rndHex = r1[0].toString(16).padStart(8, '0') + r1[1].toString(16).padStart(8, '0');
  // version 7 nibble (replace the first nibble of rndHex)
  const v7RndHex = '7' + rndHex.slice(1);
  // variant: set the two most significant bits of the next byte to 10
  const r2 = crypto.getRandomValues(new Uint32Array(2));
  let variantByte = (r2[0] >>> 24) & 0xff;
  variantByte = (variantByte & 0x3f) | 0x80;
  const tailHex = variantByte.toString(16).padStart(2, '0') + r2[0].toString(16).padStart(8, '0').slice(2) + r2[1].toString(16).padStart(8, '0');
  const hex = tsHex + v7RndHex + tailHex;
  return (
    hex.slice(0, 8) + '-' +
    hex.slice(8, 12) + '-' +
    hex.slice(12, 16) + '-' +
    hex.slice(16, 20) + '-' +
    hex.slice(20, 32)
  );
}

function storageGet(key: string): string | null {
  try { return window.localStorage.getItem(key); } catch { return null; }
}
function storageSet(key: string, val: string) {
  try { window.localStorage.setItem(key, val); } catch {}
}
function storageDel(key: string) {
  try { window.localStorage.removeItem(key); } catch {}
}

class Queue {
  private items: Event[] = [];
  private bytes = 0;
  constructor(private maxQueueBytes: number) {}
  push(e: Event) {
    const est = JSON.stringify(e).length + 1; // + newline
    this.items.push(e);
    this.bytes += est;
    return this.items.length;
  }
  drain(max: number): Event[] {
    const n = Math.min(max, this.items.length);
    const out = this.items.splice(0, n);
    // recompute bytes roughly
    this.bytes = this.items.reduce((acc, it) => acc + JSON.stringify(it).length + 1, 0);
    return out;
  }
  size() { return this.items.length; }
  overLimit() { return this.bytes >= this.maxQueueBytes; }
  snapshot() { return this.items.slice(); }
  restore(items: Event[]) { this.items = items; this.bytes = items.reduce((a,i)=>a+JSON.stringify(i).length+1,0); }
}

export class Playtics {
  private apiKey: string;
  private endpoint: string;
  private projectId: string;
  private deviceId: string;
  private userId: string | null = null;
  private userProps: Record<string, string | number | boolean | null> = {};
  private queue: Queue;
  private flushInterval: number;
  private maxBatch: number;
  private sessionGapMs: number;
  private timer: any = null;
  private sessionId: string | null = null;
  private lastActive = 0;
  private debug = false;

  constructor(opts: PlayticsOptions) {
    this.apiKey = opts.apiKey;
    this.endpoint = opts.endpoint.replace(/\/$/, '');
    this.projectId = opts.projectId;
    this.flushInterval = opts.flushIntervalMs ?? 5000;
    this.maxBatch = opts.maxBatch ?? 50;
    this.queue = new Queue(opts.maxQueueBytes ?? 512_000);
    this.sessionGapMs = opts.sessionGapMs ?? 30*60*1000;
    this.debug = !!opts.debug;

    const k = `pt_device_id_${this.projectId}`;
    this.deviceId = opts.deviceId || storageGet(k) || this.randomDeviceId();
    storageSet(k, this.deviceId);

    // restore offline queue
    const saved = storageGet(this.queueKey());
    if (saved) {
      try { this.queue.restore(JSON.parse(saved)); } catch {}
    }

    this.lastActive = nowMs();
    this.ensureTimer();
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.flush());
      window.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') this.flush(); });
    }
  }

  setUserId(userId: string | null) { this.userId = userId; }
  setUserProps(props: Record<string, string|number|boolean|null>) { this.userProps = { ...this.userProps, ...props }; }

  track(eventName: string, props?: EventProps): string {
    const ts = nowMs();
    this.rollSession(ts);
    const evt: Event = {
      event_id: uuidv7(),
      event_name: eventName,
      project_id: this.projectId,
      user_id: this.userId ?? undefined,
      device_id: this.deviceId,
      session_id: this.sessionId ?? undefined,
      ts_client: ts,
      platform: 'web',
      props: this.mergeProps(props)
    };
    this.queue.push(evt);
    this.lastActive = ts;
    if (this.debug) console.debug('[playtics] queued', evt.event_id, eventName);
    if (this.queue.overLimit()) this.flush();
    return evt.event_id;
  }

  expose(exp: string, variant: string) {
    return this.track('experiment_exposure', { exp, variant });
  }

  revenue(amount: number, currency: string, props?: EventProps) {
    return this.track('revenue', { amount, currency, ...(props||{}) });
  }

  async flush(): Promise<void> {
    if (this.queue.size() === 0) return;
    const batch = this.queue.drain(this.maxBatch);
    await this.send(batch);
    // persist remainder if any
    storageSet(this.queueKey(), JSON.stringify(this.queue.snapshot()));
  }

  shutdown() { if (this.timer) clearInterval(this.timer); this.timer = null; }

  private ensureTimer() {
    if (this.timer) return;
    this.timer = setInterval(() => { this.flush().catch(()=>{}); }, this.flushInterval);
  }

  private rollSession(ts: number) {
    if (!this.sessionId || ts - this.lastActive > this.sessionGapMs) {
      this.sessionId = uuidv7();
    }
  }

  private mergeProps(p?: EventProps) {
    return { ...this.userProps, ...(p||{}) };
  }

  private queueKey() { return `pt_queue_${this.projectId}_${this.deviceId}`; }
  private randomDeviceId() { return 'd_' + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2); }

  private async send(evts: Event[]) {
    const ndjson = evts.map(e => JSON.stringify(e)).join('\n');
    const url = `${this.endpoint}/v1/batch`;
    const headers: Record<string,string> = {
      'x-api-key': this.apiKey,
      'content-type': 'application/x-ndjson'
    };

    let body: BodyInit = ndjson;
    let useGzip = false;
    try {
      if (typeof CompressionStream !== 'undefined') {
        useGzip = true;
        const cs = new CompressionStream('gzip');
        const blob = new Blob([ndjson]);
        const stream = blob.stream().pipeThrough(cs);
        body = await new Response(stream).arrayBuffer();
        headers['content-encoding'] = 'gzip';
      }
    } catch {}

    const maxAttempts = 5;
    let backoff = 1000;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const res = await fetch(url, { method: 'POST', headers, body });
        if (res.ok) {
          if (this.debug) console.debug('[playtics] flushed', evts.length, 'gzip=', useGzip);
          return;
        }
        if (res.status === 429) {
          const retryAfter = Number(res.headers.get('retry-after')||'1');
          await sleep(retryAfter * 1000);
        } else {
          throw new Error(`HTTP ${res.status}`);
        }
      } catch (e) {
        if (attempt === maxAttempts) {
          // put back events to queue head, persist
          const rest = this.queue.snapshot();
          this.queue.restore([...evts, ...rest]);
          storageSet(this.queueKey(), JSON.stringify(this.queue.snapshot()));
          if (this.debug) console.warn('[playtics] flush failed, stored offline', e);
          return;
        }
        await sleep(backoff + jitter(250));
        backoff = Math.min(backoff * 2, 30_000);
      }
    }
  }
}

function sleep(ms: number) { return new Promise(r => setTimeout(r, ms)); }
function jitter(n: number) { return Math.floor(Math.random() * n); }
