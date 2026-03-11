package com.chat.service.utils;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    @Value("${bcrypt.semaphore.permits:2}")
    private int permits;

    private Semaphore semaphore;

    @PostConstruct
    public void init() {
        this.semaphore = new Semaphore(permits, true);
    }

    @Override
    public String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    @Override
    public boolean match(String password, String encodedPassword) {

        boolean acquired;

        try {
            acquired = semaphore.tryAcquire(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (!acquired) {
            throw new CustomException(ErrorCode.SERVER_BUSY);
        }

        try {
            return BCrypt.checkpw(password, encodedPassword);
        } finally {
            semaphore.release();
        }
    }
}
