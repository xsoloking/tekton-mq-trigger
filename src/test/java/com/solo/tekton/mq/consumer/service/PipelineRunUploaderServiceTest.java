package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.UploaderData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

@SpringBootTest
public class PipelineRunUploaderServiceTest {

    @Autowired
    PipelineRunUploaderService pipelineRunUploaderService;

    @Test
    void prepareShellScriptTest() {
        try {
            Method method = PipelineRunUploaderService.class.getDeclaredMethod("prepareShellScript", UploaderData.class);
            UploaderData data = new UploaderData();
            data.setUser("amdin");
            data.setPassword("admin@123_");
            data.setUploadDir("http://repo.devops.dev/repo/file.zip");
            data.setCompressName("file.zip");
            data.setDestFilePath("target/file.jar");
            method.setAccessible(true);
            String script = (String) method.invoke(pipelineRunUploaderService, data);
            assert (new String(Base64.getDecoder().decode(script)).contains("bash"));
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
