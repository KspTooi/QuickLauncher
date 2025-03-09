package com.ksptool.ql.biz.service;

import com.ksptool.entities.Any;
import com.ksptool.ql.biz.mapper.ModelRoleRepository;
import com.ksptool.ql.biz.mapper.ModelRpHistoryRepository;
import com.ksptool.ql.biz.mapper.ModelRpThreadRepository;
import com.ksptool.ql.biz.model.dto.GetModelRoleListDto;
import com.ksptool.ql.biz.model.dto.RecoverRpChatDto;
import com.ksptool.ql.biz.model.po.ModelRolePo;
import com.ksptool.ql.biz.model.po.ModelRpHistoryPo;
import com.ksptool.ql.biz.model.po.ModelRpThreadPo;
import com.ksptool.ql.biz.model.vo.GetModelRoleListVo;
import com.ksptool.ql.biz.model.vo.RecoverRpChatHistoryVo;
import com.ksptool.ql.biz.model.vo.RecoverRpChatVo;
import com.ksptool.ql.commons.enums.AIModelEnum;
import com.ksptool.ql.commons.exception.BizException;
import com.ksptool.ql.commons.web.PageableView;
import com.ksptool.ql.commons.web.SimpleExample;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.ksptool.entities.Entities.assign;

@Service
public class ModelRpService {

    @Autowired
    private ModelRoleRepository modelRoleRepository;

    @Autowired
    private ModelRpThreadRepository threadRepository;

    @Autowired
    private ModelRpHistoryRepository historyRepository;
    
    public PageableView<GetModelRoleListVo> getModelRoleList(GetModelRoleListDto queryDto) {
        Page<ModelRolePo> page = modelRoleRepository.getModelRoleList(
            AuthService.getCurrentUserId(),
            queryDto.getKeyword(),
            queryDto.pageRequest()
        );
        return new PageableView<>(page, GetModelRoleListVo.class);
    }

    /**
     * 恢复或创建RP对话
     */
    @Transactional
    public RecoverRpChatVo recoverRpChat(RecoverRpChatDto dto) throws BizException{

        AIModelEnum modelEnum = AIModelEnum.getByCode(dto.getModelCode());
        if (modelEnum == null) {
            throw new BizException("无效的模型代码");
        }

        // 1. 查询用户拥有的模型角色
        ModelRolePo example = new ModelRolePo();
        example.setId(dto.getModelRoleId());
        example.setUserId(AuthService.getCurrentUserId());

        ModelRolePo modelRole = modelRoleRepository.findOne(Example.of(example))
            .orElseThrow(() -> new BizException("模型角色不存在或无权访问"));
            
        // 2. 查询激活的存档
        ModelRpThreadPo thread = threadRepository.findActiveThreadByModelRoleId(dto.getModelRoleId());

        // 3. 如果没有激活的存档，创建新存档
        if (thread == null) {

            thread = new ModelRpThreadPo();
            thread.setUserId(AuthService.getCurrentUserId());
            thread.setModelCode(dto.getModelCode());
            thread.setModelRole(modelRole);
            thread.setTitle("与" + modelRole.getName() + "的对话");
            thread.setActive(1);
            thread = threadRepository.save(thread);
            
            // 创建首条消息
            if (StringUtils.isNotBlank(modelRole.getFirstMessage())) {
                ModelRpHistoryPo history = new ModelRpHistoryPo();
                history.setThread(thread);
                history.setType(1); // AI消息
                history.setRawContent(modelRole.getFirstMessage());
                history.setRpContent(modelRole.getFirstMessage()); // 这里可能需要通过RpHandler处理
                history.setSequence(1);
                historyRepository.save(history);
            }
        }
        
        // 4. 构建返回数据
        RecoverRpChatVo vo = new RecoverRpChatVo();
        vo.setThreadId(thread.getId());
        vo.setModelCode(thread.getModelCode());
        
        // 5. 获取历史记录
        List<ModelRpHistoryPo> histories = historyRepository.findByThreadIdOrderBySequence(thread.getId());
        List<RecoverRpChatHistoryVo> messages = new ArrayList<>();
        
        for (ModelRpHistoryPo history : histories) {
            RecoverRpChatHistoryVo message = new RecoverRpChatHistoryVo();
            message.setId(history.getId());
            message.setType(history.getType());
            message.setRawContent(history.getRawContent());
            
            // 设置发送者信息
            if (history.getType() == 0) { // 用户消息
                message.setName(thread.getUserRole().getName());
                message.setAvatarPath(thread.getUserRole().getAvatarPath());
            }

            // AI消息
            if(history.getType() == 1){
                message.setName(modelRole.getName());
                message.setAvatarPath(modelRole.getAvatarPath());
            }
            
            messages.add(message);
        }
        
        vo.setMessages(messages);
        return vo;
    }
} 