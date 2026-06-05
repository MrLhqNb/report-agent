package com.reportagent.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportagent.entity.ScheduledTask;
import com.reportagent.mapper.ScheduledTaskMapper;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTaskService extends ServiceImpl<ScheduledTaskMapper, ScheduledTask> {
}
