package com.qiniu.voice_calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTagRequest {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名最多50个字符")
    private String name;

    private String color;
}
