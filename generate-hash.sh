#!/bin/bash

# Create a temporary Java file to generate bcrypt hash
cat > GenerateHash.java << 'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String password = "Admin@123456";
        String hashed = encoder.encode(password);
        System.out.println(hashed);
    }
}
EOF

# Compile and run
cd "$(pwd)"
# Note: This won't work easily without Spring on classpath
# Instead, use a known bcrypt hash or install bcrypt via npm

# For now, let's just output what we'd try
echo "Password: Admin@123456"
echo "Would generate bcrypt hash with cost 10"
