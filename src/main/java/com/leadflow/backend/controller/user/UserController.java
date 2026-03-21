package com.leadflow.backend.controller.user;

import com.leadflow.backend.dto.user.UpdateUserRequest;
import com.leadflow.backend.dto.user.UserResponse;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.service.user.UserService;
import com.leadflow.backend.mapper.user.UserMapper;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    /* ======================================================
       LIST (PAGINADO)
       ====================================================== */

    @GetMapping
    public ResponseEntity<Page<UserResponse>> list(Pageable pageable) {
        Page<UserResponse> response = userService
                .listActiveUsers(pageable)
                .map(userMapper::toResponse);

        return ResponseEntity.ok(response);
    }

    /* ======================================================
       GET BY ID
       ====================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        User user = userService.getByIdOrThrow(id);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    /* ======================================================
       UPDATE
       ====================================================== */

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        User user = userService.updateUser(
                id,
                request.getName(),
                request.getEmail(),
                request.getRoleId()
        );

        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    /* ======================================================
       DELETE (SOFT)
       ====================================================== */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}