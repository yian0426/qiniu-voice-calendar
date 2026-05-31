package com.qiniu.voice_calendar.service;

import com.qiniu.voice_calendar.dto.CreateTagRequest;
import com.qiniu.voice_calendar.dto.TagVO;
import com.qiniu.voice_calendar.dto.UpdateTagRequest;

import java.util.List;

public interface TagService {
    List<TagVO> listTags(Long userId);
    TagVO createTag(Long userId, CreateTagRequest request);
    TagVO updateTag(Long userId, Long tagId, UpdateTagRequest request);
    void deleteTag(Long userId, Long tagId);
}
