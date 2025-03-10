package com.ksptool.ql.biz.service;

import com.ksptool.entities.Entities;
import com.ksptool.ql.biz.mapper.ModelRoleRepository;
import com.ksptool.ql.biz.mapper.ModelRpHistoryRepository;
import com.ksptool.ql.biz.mapper.ModelRpSegmentRepository;
import com.ksptool.ql.biz.mapper.ModelRpThreadRepository;
import com.ksptool.ql.biz.model.dto.BatchRpCompleteDto;
import com.ksptool.ql.biz.model.dto.GetModelRoleListDto;
import com.ksptool.ql.biz.model.dto.RecoverRpChatDto;
import com.ksptool.ql.biz.model.dto.DeActiveThreadDto;
import com.ksptool.ql.biz.model.po.ModelRolePo;
import com.ksptool.ql.biz.model.po.ModelRpHistoryPo;
import com.ksptool.ql.biz.model.po.ModelRpSegmentPo;
import com.ksptool.ql.biz.model.po.ModelRpThreadPo;
import com.ksptool.ql.biz.model.vo.GetModelRoleListVo;
import com.ksptool.ql.biz.model.vo.RecoverRpChatHistoryVo;
import com.ksptool.ql.biz.model.vo.RecoverRpChatVo;
import com.ksptool.ql.biz.model.vo.RpSegmentVo;
import com.ksptool.ql.biz.service.panel.PanelApiKeyService;
import com.ksptool.ql.commons.enums.AIModelEnum;
import com.ksptool.ql.commons.exception.BizException;
import com.ksptool.ql.commons.web.PageableView;
import com.ksptool.ql.commons.utils.HttpClientUtils;
import com.ksptool.ql.biz.model.dto.ModelChatParam;
import com.ksptool.ql.biz.model.dto.ModelChatParamHistory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.ksptool.ql.biz.model.vo.ModelChatContext;

import static com.ksptool.entities.Entities.assign;

@Slf4j
@Service
public class ModelRpService {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // 线程安全的容器，记录RP聊天状态 <threadId, contextId>
    private final ConcurrentHashMap<Long, String> rpThreadToContextIdMap = new ConcurrentHashMap<>();
    
    // 终止列表，存储已经被终止的contextId
    private final ConcurrentHashMap<String, Boolean> terminatedContextIds = new ConcurrentHashMap<>();

    @Autowired
    private ModelRoleRepository modelRoleRepository;

    @Autowired
    private ModelRpThreadRepository threadRepository;

    @Autowired
    private ModelRpHistoryRepository historyRepository;
    
    @Autowired
    private ModelRpSegmentRepository segmentRepository;

    @Autowired
    private ModelGeminiService modelGeminiService;
    
    @Autowired
    private ConfigService configService;
    
    @Autowired
    private PanelApiKeyService panelApiKeyService;

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

    /**
     * 取消激活RP对话
     */
    @Transactional
    public void deActiveThread(DeActiveThreadDto dto) throws BizException {
        // 1. 查询用户拥有的存档
        ModelRpThreadPo thread = threadRepository.findById(dto.getThreadId())
            .orElseThrow(() -> new BizException("存档不存在"));

        // 2. 验证权限
        if (!thread.getUserId().equals(AuthService.getCurrentUserId())) {
            throw new BizException("无权操作此存档");
        }

        // 3. 检查是否已经是非激活状态
        if (thread.getActive() == 0) {
            throw new BizException("存档已经是非激活状态");
        }

        // 4. 设置为非激活状态
        thread.setActive(0);
        threadRepository.save(thread);
    }

    /**
     * 批量完成RP对话
     * 处理发送消息、查询响应流和终止AI响应等操作
     * @param dto 批量完成RP对话的请求参数
     * @return 返回对话片段信息
     * @throws BizException 业务异常
     */
    @Transactional
    public RpSegmentVo rpCompleteBatch(BatchRpCompleteDto dto) throws BizException {
        // 根据queryKind调用不同的处理方法
        if (dto.getQueryKind() == 0) {
            // 发送消息
            return rpCompleteSendBatch(dto);
        }
        
        if (dto.getQueryKind() == 1) {
            // 查询响应流
            return rpCompleteQueryBatch(dto);
        }
        
        if (dto.getQueryKind() == 2) {
            // 终止AI响应
            rpCompleteTerminateBatch(dto);
            return null;
        }
        
        throw new BizException("无效的查询类型");
    }
    
    /**
     * 发送RP对话消息
     * @param dto 批量完成RP对话的请求参数
     * @return 返回对话片段信息
     * @throws BizException 业务异常
     */
    private RpSegmentVo rpCompleteSendBatch(BatchRpCompleteDto dto) throws BizException {
        Long threadId = dto.getThread();

        // 检查该会话是否正在处理中
        if (rpThreadToContextIdMap.containsKey(threadId)) {
            throw new BizException("该会话正在处理中，请等待AI响应完成");
        }

        try {
            // 获取并验证模型配置
            AIModelEnum modelEnum = AIModelEnum.getByCode(dto.getModel());
            if (modelEnum == null) {
                throw new BizException("无效的模型代码");
            }

            // 获取当前用户ID
            Long userId = AuthService.getCurrentUserId();

            // 获取API Key
            String apiKey = panelApiKeyService.getApiKey(modelEnum.getCode(), userId);
            if (StringUtils.isBlank(apiKey)) {
                throw new BizException("未配置API Key");
            }

            // 获取会话
            ModelRpThreadPo thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new BizException("会话不存在"));
                
            // 验证用户权限
            if (!thread.getUserId().equals(userId)) {
                throw new BizException("无权访问该会话");
            }
            
            // 获取角色信息
            ModelRolePo modelRole = thread.getModelRole();
            if (modelRole == null) {
                throw new BizException("角色信息不存在");
            }

            // 保存用户消息
            ModelRpHistoryPo userHistory = new ModelRpHistoryPo();
            userHistory.setThread(thread);
            userHistory.setType(0); // 用户消息
            userHistory.setRawContent(dto.getMessage());
            userHistory.setRpContent(dto.getMessage()); // 这里可能需要通过RpHandler处理
            userHistory.setSequence(historyRepository.findMaxSequenceByThreadId(thread.getId()) + 1);
            historyRepository.save(userHistory);

            // 清理之前的片段（如果有）
            segmentRepository.deleteByThreadId(thread.getId());

            // 创建开始片段并返回
            ModelRpSegmentPo startSegment = new ModelRpSegmentPo();
            startSegment.setUserId(userId);
            startSegment.setThread(thread);
            startSegment.setSequence(1);
            startSegment.setContent(null);
            startSegment.setStatus(0); // 未读状态
            startSegment.setType(0); // 开始类型
            segmentRepository.save(startSegment);

            // 获取配置
            String baseKey = "ai.model.cfg." + modelEnum.getCode() + ".";
            String proxyConfig = configService.get(baseKey + "proxy", userId);

            // 创建HTTP客户端
            OkHttpClient client = HttpClientUtils.createHttpClient(proxyConfig, 60);

            // 创建请求参数
            ModelChatParam modelChatParam = new ModelChatParam();
            modelChatParam.setMessage(dto.getMessage());
            modelChatParam.setUrl(GEMINI_BASE_URL + modelEnum.getCode() + ":streamGenerateContent");
            modelChatParam.setApiKey(apiKey);
            
            // 获取历史记录并转换为ModelChatParamHistory
            List<ModelRpHistoryPo> histories = historyRepository.findByThreadIdOrderBySequence(thread.getId());
            List<ModelChatParamHistory> paramHistories = new ArrayList<>();
            
            for (ModelRpHistoryPo history : histories) {
                ModelChatParamHistory paramHistory = new ModelChatParamHistory();
                paramHistory.setRole(history.getType()); // 设置角色类型：0-用户 1-AI助手
                paramHistory.setContent(history.getRawContent());
                paramHistories.add(paramHistory);
            }
            
            modelChatParam.setHistories(paramHistories);

            // 异步调用ModelGeminiService发送流式请求
            modelGeminiService.sendMessageStream(
                    client,
                    modelChatParam,
                    // 使用封装后的回调函数
                    onModelRpMessageRcv(thread, userId)
            );

            // 返回用户消息作为第一次响应
            RpSegmentVo vo = new RpSegmentVo();
            vo.setThreadId(thread.getId());
            vo.setHistoryId(userHistory.getId());
            vo.setRole(0); // 用户角色
            vo.setSequence(startSegment.getSequence());
            vo.setContent(dto.getMessage()); // 返回用户的消息内容
            vo.setType(0); // 起始类型
            
            // 设置角色信息
            vo.setRoleId(thread.getUserRole() != null ? thread.getUserRole().getId() : null);
            vo.setRoleName(thread.getUserRole() != null ? thread.getUserRole().getName() : "用户");
            vo.setRoleAvatarPath(thread.getUserRole() != null ? thread.getUserRole().getAvatarPath() : null);
            
            return vo;

        } catch (Exception e) {
            // 发生异常时清理会话状态
            rpThreadToContextIdMap.remove(threadId);

            // 清理所有片段
            try {
                segmentRepository.deleteByThreadId(threadId);
            } catch (Exception ex) {
                log.error("清理RP对话片段失败", ex);
            }

            throw new BizException("发送RP对话消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询RP对话响应流
     * @param dto 批量完成RP对话的请求参数
     * @return 返回对话片段信息
     * @throws BizException 业务异常
     */
    private RpSegmentVo rpCompleteQueryBatch(BatchRpCompleteDto dto) throws BizException {
        // 暂时返回null，后续实现
        return null;
    }
    
    /**
     * 终止RP对话响应
     * @param dto 批量完成RP对话的请求参数
     * @throws BizException 业务异常
     */
    private void rpCompleteTerminateBatch(BatchRpCompleteDto dto) throws BizException {
        // 暂时不实现，后续添加
    }

    /**
     * 创建处理模型消息回调的Consumer
     * @param thread RP聊天线程
     * @param userId 用户ID
     * @return 处理模型消息的Consumer
     */
    private Consumer<ModelChatContext> onModelRpMessageRcv(ModelRpThreadPo thread, Long userId) {
        return context -> {
            try {
                // 从ModelChatContext中获取contextId
                String contextId = context.getContextId();
                Long threadId = thread.getId();
                String modelCode = context.getModelCode();
                AIModelEnum modelEnum = AIModelEnum.getByCode(modelCode);

                // 检查该contextId是否已被终止
                if (terminatedContextIds.containsKey(contextId)) {
                    // 如果是完成或错误回调，从终止列表中移除
                    if (context.getType() == 1 || context.getType() == 2) {
                        terminatedContextIds.remove(contextId);
                    }
                    return;
                }

                // 首次收到消息时，将contextId存入映射表
                if (!rpThreadToContextIdMap.containsValue(contextId)) {
                    rpThreadToContextIdMap.put(threadId, contextId);
                }

                // 获取当前最大序号
                int nextSequence = segmentRepository.findMaxSequenceByThreadId(threadId) + 1;
                
                // 根据context.type处理不同类型的消息
                if (context.getType() == 0) {
                    // 数据类型 - 创建数据片段
                    ModelRpSegmentPo dataSegment = new ModelRpSegmentPo();
                    dataSegment.setUserId(userId);
                    dataSegment.setThread(thread);
                    dataSegment.setSequence(nextSequence);
                    dataSegment.setContent(context.getContent());
                    dataSegment.setStatus(0); // 未读状态
                    dataSegment.setType(1); // 数据类型
                    segmentRepository.save(dataSegment);
                    return;
                }
                
                if (context.getType() == 1) {
                    // 保存AI响应到历史记录
                    ModelRpHistoryPo aiHistory = new ModelRpHistoryPo();
                    aiHistory.setThread(thread);
                    aiHistory.setType(1); // AI消息
                    aiHistory.setRawContent(context.getContent());
                    aiHistory.setRpContent(context.getContent()); // 这里可能需要通过RpHandler处理
                    aiHistory.setSequence(historyRepository.findMaxSequenceByThreadId(threadId) + 1);
                    historyRepository.save(aiHistory);
                    
                    // 完成类型 - 创建结束片段
                    ModelRpSegmentPo endSegment = new ModelRpSegmentPo();
                    endSegment.setUserId(userId);
                    endSegment.setThread(thread);
                    endSegment.setSequence(nextSequence);
                    endSegment.setContent(null);
                    endSegment.setStatus(0); // 未读状态
                    endSegment.setType(2); // 结束类型
                    endSegment.setHistoryId(aiHistory.getId()); // 设置关联的历史记录ID
                    segmentRepository.save(endSegment);

                    // 更新会话使用的模型
                    if (modelEnum != null) {
                        thread.setModelCode(modelEnum.getCode());
                        threadRepository.save(thread);
                    }
                    
                    // 清理会话状态
                    rpThreadToContextIdMap.remove(threadId);
                    return;
                }
                
                if (context.getType() == 2) {
                    // 错误类型 - 创建错误片段
                    ModelRpSegmentPo errorSegment = new ModelRpSegmentPo();
                    errorSegment.setUserId(userId);
                    errorSegment.setThread(thread);
                    errorSegment.setSequence(nextSequence);
                    errorSegment.setContent(context.getException() != null ? "AI响应错误: " + context.getException().getMessage() : "AI响应错误");
                    errorSegment.setStatus(0); // 未读状态
                    errorSegment.setType(10); // 错误类型
                    segmentRepository.save(errorSegment);

                    // 清理会话状态
                    rpThreadToContextIdMap.remove(threadId);
                }
            } catch (Exception e) {
                log.error("处理RP对话片段失败", e);
            }
        };
    }
}