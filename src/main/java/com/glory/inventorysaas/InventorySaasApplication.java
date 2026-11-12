package com.glory.inventorysaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class InventorySaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventorySaasApplication.class, args);
    }

}
