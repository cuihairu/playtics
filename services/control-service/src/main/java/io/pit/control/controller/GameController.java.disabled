package io.pit.control.controller;

import io.pit.control.dto.ApiResponse;
import io.pit.control.dto.GameDTO;
import io.pit.control.jpa.GameEntity;
import io.pit.control.security.JwtAuthenticationFilter;
import io.pit.control.service.GameService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游戏管理控制器
 * 提供游戏的CRUD操作和生命周期管理
 */
@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);

    @Autowired
    private GameService gameService;

    /**
     * 创建游戏
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORG_ADMIN') or @gameController.canCreateGameInOrg(#gameDTO.orgId, authentication)")
    public ResponseEntity<ApiResponse<GameDTO>> createGame(
            @Valid @RequestBody GameDTO gameDTO,
            Authentication authentication) {

        logger.info("Creating game: {} in organization: {} by user: {}",
            gameDTO.name, gameDTO.orgId, authentication.getName());

        try {
            GameDTO created = gameService.createGame(gameDTO);

            logger.info("Game created successfully: {} (ID: {})",
                created.name, created.id);

            return ResponseEntity.status(201)
                .body(ApiResponse.success("Game created successfully", created));

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create game: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot create game: {}", e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to create game: {}", gameDTO.name, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create game"));
        }
    }

    /**
     * 根据组织获取游戏列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GameDTO>>> getGamesByOrganization(
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        try {
            // 如果没有指定orgId，从认证信息中获取
            if (orgId == null) {
                JwtAuthenticationFilter.PitAuthenticationDetails details =
                    (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();
                orgId = details.orgId;
            }

            // 权限检查
            if (!canAccessOrganization(orgId, authentication)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Access denied to organization"));
            }

            // 创建分页参数
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<GameDTO> games = gameService.getGamesByOrganization(orgId, pageable);

            return ResponseEntity.ok(
                ApiResponse.page(
                    games.getContent(),
                    games.getTotalElements(),
                    page,
                    size
                )
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve games for organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve games"));
        }
    }

    /**
     * 搜索游戏
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<GameDTO>>> searchGames(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        try {
            // 创建分页和排序参数
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<GameDTO> games;

            if (orgId != null) {
                // 权限检查
                if (!canAccessOrganization(orgId, authentication)) {
                    return ResponseEntity.status(403)
                        .body(ApiResponse.forbidden("Access denied to organization"));
                }
                games = gameService.searchGamesInOrganization(orgId, q, pageable);
            } else {
                // 超级管理员可以搜索所有游戏
                JwtAuthenticationFilter.PitAuthenticationDetails details =
                    (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();
                if (!details.isSuperAdmin()) {
                    return ResponseEntity.status(403)
                        .body(ApiResponse.forbidden("Access denied"));
                }
                games = gameService.searchGames(q, pageable);
            }

            return ResponseEntity.ok(
                ApiResponse.page(
                    games.getContent(),
                    games.getTotalElements(),
                    page,
                    size
                )
            );

        } catch (Exception e) {
            logger.error("Failed to search games with query: {}", q, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to search games"));
        }
    }

    /**
     * 根据ID获取游戏
     */
    @GetMapping("/{gameId}")
    @PreAuthorize("@gameController.canAccessGame(#gameId, authentication)")
    public ResponseEntity<ApiResponse<GameDTO>> getGame(
            @PathVariable String gameId,
            Authentication authentication) {

        try {
            Optional<GameDTO> game = gameService.getGame(gameId);

            if (game.isPresent()) {
                return ResponseEntity.ok(
                    ApiResponse.success("Game retrieved successfully", game.get())
                );
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("Game not found"));
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve game"));
        }
    }

    /**
     * 更新游戏
     */
    @PutMapping("/{gameId}")
    @PreAuthorize("@gameController.canManageGame(#gameId, authentication)")
    public ResponseEntity<ApiResponse<GameDTO>> updateGame(
            @PathVariable String gameId,
            @Valid @RequestBody GameDTO gameDTO,
            Authentication authentication) {

        logger.info("Updating game: {} by user: {}",
            gameId, authentication.getName());

        try {
            GameDTO updated = gameService.updateGame(gameId, gameDTO);

            logger.info("Game updated successfully: {}", gameId);
            return ResponseEntity.ok(
                ApiResponse.success("Game updated successfully", updated)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update game"));
        }
    }

    /**
     * 删除游戏
     */
    @DeleteMapping("/{gameId}")
    @PreAuthorize("@gameController.canDeleteGame(#gameId, authentication)")
    public ResponseEntity<ApiResponse<String>> deleteGame(
            @PathVariable String gameId,
            Authentication authentication) {

        logger.info("Deleting game: {} by user: {}",
            gameId, authentication.getName());

        try {
            gameService.deleteGame(gameId);

            logger.info("Game deleted successfully: {}", gameId);
            return ResponseEntity.ok(
                ApiResponse.success("Game deleted successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot delete game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to delete game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete game"));
        }
    }

    /**
     * 发布游戏
     */
    @PostMapping("/{gameId}/publish")
    @PreAuthorize("@gameController.canManageGame(#gameId, authentication)")
    public ResponseEntity<ApiResponse<GameDTO>> publishGame(
            @PathVariable String gameId,
            Authentication authentication) {

        logger.info("Publishing game: {} by user: {}",
            gameId, authentication.getName());

        try {
            GameDTO published = gameService.publishGame(gameId);

            logger.info("Game published successfully: {}", gameId);
            return ResponseEntity.ok(
                ApiResponse.success("Game published successfully", published)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to publish game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot publish game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to publish game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to publish game"));
        }
    }

    /**
     * 下线游戏
     */
    @PostMapping("/{gameId}/unpublish")
    @PreAuthorize("@gameController.canManageGame(#gameId, authentication)")
    public ResponseEntity<ApiResponse<GameDTO>> unpublishGame(
            @PathVariable String gameId,
            Authentication authentication) {

        logger.info("Unpublishing game: {} by user: {}",
            gameId, authentication.getName());

        try {
            GameDTO unpublished = gameService.unpublishGame(gameId);

            logger.info("Game unpublished successfully: {}", gameId);
            return ResponseEntity.ok(
                ApiResponse.success("Game unpublished successfully", unpublished)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to unpublish game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot unpublish game {}: {}", gameId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to unpublish game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to unpublish game"));
        }
    }

    /**
     * 根据状态获取游戏
     */
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<GameDTO>>> getGamesByStatus(
            @PathVariable GameEntity.GameStatus status) {

        try {
            List<GameDTO> games = gameService.getGamesByStatus(status);

            return ResponseEntity.ok(
                ApiResponse.success("Games retrieved successfully", games)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve games by status: {}", status, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve games"));
        }
    }

    /**
     * 根据类型获取游戏
     */
    @GetMapping("/by-genre/{genre}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<GameDTO>>> getGamesByGenre(
            @PathVariable GameEntity.GameGenre genre) {

        try {
            List<GameDTO> games = gameService.getGamesByGenre(genre);

            return ResponseEntity.ok(
                ApiResponse.success("Games retrieved successfully", games)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve games by genre: {}", genre, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve games"));
        }
    }

    /**
     * 根据平台获取游戏
     */
    @GetMapping("/by-platform/{platform}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<GameDTO>>> getGamesByPlatform(
            @PathVariable GameEntity.GamePlatform platform) {

        try {
            List<GameDTO> games = gameService.getGamesByPlatform(platform);

            return ResponseEntity.ok(
                ApiResponse.success("Games retrieved successfully", games)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve games by platform: {}", platform, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve games"));
        }
    }

    /**
     * 获取游戏统计信息
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGameStatistics() {

        try {
            Map<String, Object> statistics = gameService.getGameStatistics();

            return ResponseEntity.ok(
                ApiResponse.success("Statistics retrieved successfully", statistics)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve game statistics", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    /**
     * 获取指定组织的游戏统计
     */
    @GetMapping("/statistics/{orgId}")
    @PreAuthorize("@gameController.canAccessOrganization(#orgId, authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGameStatisticsByOrganization(
            @PathVariable String orgId,
            Authentication authentication) {

        try {
            Map<String, Object> statistics = gameService.getGameStatisticsByOrganization(orgId);

            return ResponseEntity.ok(
                ApiResponse.success("Statistics retrieved successfully", statistics)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve game statistics for organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    // 权限检查方法

    public boolean canAccessOrganization(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            return details.isSuperAdmin() || orgId.equals(details.orgId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canCreateGameInOrg(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            return details.isSuperAdmin() ||
                   (details.isOrgAdmin() && orgId.equals(details.orgId));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canAccessGame(String gameId, Authentication authentication) {
        try {
            Optional<GameDTO> game = gameService.getGame(gameId);
            if (game.isEmpty()) {
                return false;
            }

            return canAccessOrganization(game.get().orgId, authentication);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageGame(String gameId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            if (details.isSuperAdmin()) {
                return true;
            }

            Optional<GameDTO> game = gameService.getGame(gameId);
            if (game.isEmpty()) {
                return false;
            }

            return details.isOrgAdmin() && game.get().orgId.equals(details.orgId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canDeleteGame(String gameId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 只有超级管理员和组织管理员可以删除游戏
            return details.isSuperAdmin() ||
                   (details.isOrgAdmin() && canManageGame(gameId, authentication));
        } catch (Exception e) {
            return false;
        }
    }
}