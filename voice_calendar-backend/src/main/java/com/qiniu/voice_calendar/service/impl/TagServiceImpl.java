package com.qiniu.voice_calendar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiniu.voice_calendar.dto.CreateTagRequest;
import com.qiniu.voice_calendar.dto.TagVO;
import com.qiniu.voice_calendar.dto.UpdateTagRequest;
import com.qiniu.voice_calendar.entity.EventTag;
import com.qiniu.voice_calendar.entity.Tag;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.mapper.EventTagMapper;
import com.qiniu.voice_calendar.mapper.TagMapper;
import com.qiniu.voice_calendar.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final EventTagMapper eventTagMapper;

    @Override
    public List<TagVO> listTags(Long userId) {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).orderByAsc(Tag::getCreatedAt));

        // Count events per tag
        Map<Long, Long> countMap = eventTagMapper.selectList(
                new LambdaQueryWrapper<EventTag>().in(EventTag::getTagId,
                        tags.stream().map(Tag::getId).toList())
        ).stream().collect(Collectors.groupingBy(EventTag::getTagId, Collectors.counting()));

        return tags.stream()
                .map(t -> TagVO.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .color(t.getColor())
                        .eventCount(countMap.getOrDefault(t.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TagVO createTag(Long userId, CreateTagRequest request) {
        String name = request.getName().trim();
        Long exists = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).eq(Tag::getName, name));
        if (exists > 0) {
            throw new BusinessException(409, "标签名已存在");
        }

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(request.getColor() != null ? request.getColor() : "#909399");
        tagMapper.insert(tag);

        log.info("Tag created: id={}, name={}, userId={}", tag.getId(), name, userId);
        return TagVO.builder().id(tag.getId()).name(tag.getName()).color(tag.getColor()).eventCount(0L).build();
    }

    @Override
    @Transactional
    public TagVO updateTag(Long userId, Long tagId, UpdateTagRequest request) {
        Tag tag = findOwnTag(userId, tagId);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            // Check duplicate
            Long dup = tagMapper.selectCount(
                    new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getUserId, userId)
                            .eq(Tag::getName, newName)
                            .ne(Tag::getId, tagId));
            if (dup > 0) {
                throw new BusinessException(409, "标签名已存在");
            }
            tag.setName(newName);
        }
        if (request.getColor() != null) {
            tag.setColor(request.getColor());
        }
        tagMapper.updateById(tag);

        Long count = eventTagMapper.selectCount(
                new LambdaQueryWrapper<EventTag>().eq(EventTag::getTagId, tagId));

        return TagVO.builder().id(tag.getId()).name(tag.getName()).color(tag.getColor()).eventCount(count).build();
    }

    @Override
    @Transactional
    public void deleteTag(Long userId, Long tagId) {
        findOwnTag(userId, tagId);
        // cascade: event_tags rows are deleted by FK ON DELETE CASCADE
        tagMapper.deleteById(tagId);
        log.info("Tag deleted: id={}, userId={}", tagId, userId);
    }

    private Tag findOwnTag(Long userId, Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(404, "标签不存在");
        }
        if (!tag.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该标签");
        }
        return tag;
    }
}
