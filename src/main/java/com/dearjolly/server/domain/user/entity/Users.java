package com.dearjolly.server.domain.user.entity;

import static com.dearjolly.server.domain.user.enums.Role.ROLE_ADMIN;
import static com.dearjolly.server.domain.user.enums.Role.ROLE_USER;

import com.dearjolly.server.domain.user.enums.Role;
import com.dearjolly.server.domain.letter.entity.Letters;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "social_id", nullable = false, length = 30)
    private String socialId;

    @Column(name = "provider", nullable = false, length = 10)
    private String provider;

    @Column(name = "email", nullable = false, length = 50)
    private String email;

    @Column(name = "nickname", nullable = false, length = 15)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Letters> letters = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private Users(String socialId, String provider, String email, String nickname, Role role) {
        this.socialId = socialId;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    // ========= 생성 메서드 =========
    public static Users create(String socialId, String provider, String email, String nickname) {
        return new Users(socialId, provider, email, nickname, ROLE_USER);
    }

    public static Users createAdmin(String socialId, String provider, String email, String nickname) {
        return new Users(socialId, provider, email, nickname, ROLE_ADMIN);
    }

    // ========= 연관관계 메서드 =========
    public void addLetter(Letters letter) {
        this.letters.add(letter);
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
