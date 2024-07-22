package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.AliOssUtil;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {
    @Resource
    AliOssUtil aliOssUtil;

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            //获取文件输入流
            byte[] bytes = image.getBytes();
            //获取用户id
            Long id = UserHolder.getUser().getId();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename, id);
            //上传文件
            String upload = aliOssUtil.upload(bytes, fileName);
            // 返回结果
            return Result.ok(upload);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String url) {
        aliOssUtil.deleteFileFromOSS(url);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename, Long userId) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        // 生成文件名
        return StrUtil.format("blogs/images/{}/{}.{}", userId, name, suffix);
    }
}
