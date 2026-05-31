package com.qiniu.voice_calendar.controller;

import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.CreateTagRequest;
import com.qiniu.voice_calendar.dto.TagVO;
import com.qiniu.voice_calendar.dto.UpdateTagRequest;
import com.qiniu.voice_calendar.service.TagService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<TagVO>> listTags() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return Result.ok(tagService.listTags(userId));
    }

    @PostMapping
    public Result<TagVO> createTag(@Valid @RequestBody CreateTagRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        TagVO tag = tagService.createTag(userId, request);
        return Result.ok("创建成功", tag);
    }

    @PutMapping("/{id}")
    public Result<TagVO> updateTag(@PathVariable Long id, @RequestBody UpdateTagRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        TagVO tag = tagService.updateTag(userId, id, request);
        return Result.ok("修改成功", tag);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        tagService.deleteTag(userId, id);
        return Result.ok("已删除");
    }
}
