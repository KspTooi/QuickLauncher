package com.ksptool.ql.biz.service;

import com.ksptool.ql.biz.mapper.UserRepository;
import com.ksptool.ql.biz.mapper.UserSessionRepository;
import com.ksptool.ql.biz.model.po.UserPo;
import com.ksptool.ql.biz.model.po.UserSessionPo;
import com.ksptool.ql.commons.WebUtils;
import com.ksptool.ql.commons.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Value("${session.expires}")
    private long expiresInSeconds;

    public UserPo verifyUser(HttpServletRequest hsr){

        String token = WebUtils.getCookieValue(hsr, "token");

        if (token == null) {
            return null; // Cookie中没有token
        }

        Long userId = verifyToken(token);

        if (userId == null) {
            return null; // Token无效
        }

        UserPo user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return null; // 用户不存在
        }

        return user; // Token有效，返回用户实例
    }


    public String loginByPassword(String username, String password) throws BizException {
        // 根据用户名查询用户
        UserPo user = userRepository.findByUsername(username);
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        // 使用用户名作为盐，对密码进行加密：password + username
        String salted = password + username;
        String hashedPassword = hashSHA256(salted);
        if (!hashedPassword.equals(user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        // 登录成功，创建或返回 token
        return createToken(user.getId());
    }


    public String createToken(Long userId) {
        Date now = new Date();
        // 查询当前用户是否已有会话
        UserSessionPo existingSession = userSessionRepository.findByUserId(userId);
        if (existingSession != null) {
            // 如果 token 未过期，则直接返回当前 token
            if (existingSession.getExpiresAt().after(now)) {
                return existingSession.getToken();
            } else {
                // 已过期，删除旧记录
                userSessionRepository.delete(existingSession);
            }
        }
        // 创建新的 token
        String token = UUID.randomUUID().toString();
        UserSessionPo newSession = new UserSessionPo();
        newSession.setUserId(userId);
        newSession.setToken(token);
        newSession.setExpiresAt(new Date(System.currentTimeMillis() + expiresInSeconds * 1000));
        userSessionRepository.save(newSession);
        return token;
    }

    public Long verifyToken(String token) {
        UserSessionPo userSession = userSessionRepository.findByToken(token);
        if (userSession == null || userSession.getExpiresAt().before(new Date())) {
            return null; // Token无效
        } else {
            return userSession.getUserId();
        }
    }

    public void destroyToken(String token) {
        userSessionRepository.deleteByToken(token);
    }

    private String hashSHA256(String input) throws BizException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException("密码加密失败", e);
        }
    }

    /**
     * 用户注销，清除数据库中的 session
     * @param user 当前用户
     */
    public void logout(UserPo user) {
        // 清除用户的 session
        UserSessionPo query = new UserSessionPo();
        query.setUserId(user.getId());
        Example<UserSessionPo> example = Example.of(query);
        userSessionRepository.deleteAll(userSessionRepository.findAll(example));
    }

}