package com.hmdp.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import java.io.ByteArrayInputStream;
import java.net.URL;

@Data
@Slf4j
@Configuration
public class AliOssUtil {
    private String endpoint = "oss-cn-hangzhou.aliyuncs.com";
    private String accessKeyId = "LTAI5tECcdeFwVWasGEab7Qs";
    private String accessKeySecret = "WBUrThYkUIe8U8FPLYDF6BjW52L3nT";
    private String bucketName = "dp-project";

    public String upload(byte[] bytes, String objectName) {

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建PutObject请求。
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to hmdpunicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        //文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件上传到:{}", stringBuilder);
        return stringBuilder.toString();
    }

    public void deleteFileFromOSS(String Url) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            //创建对象
            //拆解URL
            URL url = new URL(Url);
            String path = url.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            // 删除文件
            ossClient.deleteObject(this.bucketName, path);
            System.out.println("文件删除成功：" + path);
        } catch (OSSException oe) {
            log.info("Error message: " + oe.getErrorMessage());
            log.info("Error code: " + oe.getErrorCode());
            log.info("Request ID: " + oe.getRequestId());
            log.info("Host ID: " + oe.getHostId());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ossClient.shutdown();
        }
    }
}
