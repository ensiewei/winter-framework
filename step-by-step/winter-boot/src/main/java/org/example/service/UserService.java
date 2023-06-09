package org.example.service;

import org.example.User;
import org.example.annotation.Autowired;
import org.example.annotation.Component;
import org.example.annotation.Transactional;
import org.example.jdbc.JdbcTemplate;

import java.util.List;

@Component
@Transactional
public class UserService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    public void initDb() {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    email VARCHAR(50) PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    password VARCHAR(50) NOT NULL
                )
                """;
        jdbcTemplate.update(sql);
    }

    public User getUser(String email) {
        return jdbcTemplate.queryForObject("SELECT * FROM users WHERE email = ?", User.class, email);
    }

    public List<User> getUsers() {
        return jdbcTemplate.queryForList("SELECT email, name FROM users", User.class);
    }

    public User createUser(String email, String name, String password) {
        User user = new User();
        user.email = email.strip().toLowerCase();
        user.name = name.strip();
        user.password = password;
        jdbcTemplate.update("INSERT INTO users (email, name, password) VALUES (?, ?, ?)", user.email, user.name, user.password);
        return user;
    }

}
