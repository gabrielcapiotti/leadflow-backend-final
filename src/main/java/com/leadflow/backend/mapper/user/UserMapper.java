package com.leadflow.backend.mapper.user;

import com.leadflow.backend.dto.user.UserResponse;
import com.leadflow.backend.entities.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String roleName = user.getRole() != null
                ? user.getRole().getName()
                : null;

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roleName
        );
    }
}