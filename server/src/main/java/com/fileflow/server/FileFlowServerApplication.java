package com.fileflow.server;

import com.fileflow.server.config.ProcessingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProcessingProperties.class)
public class FileFlowServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileFlowServerApplication.class, args);
    }
}
