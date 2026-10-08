package com.zwskchat.zwskchat;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan(basePackages = "com.zwskchat.zwskchat", annotationClass = Mapper.class)
@ConfigurationPropertiesScan("com.zwskchat.zwskchat.config.properties")
public class ZwskchatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZwskchatApplication.class, args);
    }

}
