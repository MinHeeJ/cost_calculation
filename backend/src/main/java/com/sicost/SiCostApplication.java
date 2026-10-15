package com.sicost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 공개 조회 서비스와 공통 기반을 시작하는 MVC 애플리케이션이다. */
@SpringBootApplication
public class SiCostApplication {
    public static void main(String[] args) {
        SpringApplication.run(SiCostApplication.class, args);
    }
}
