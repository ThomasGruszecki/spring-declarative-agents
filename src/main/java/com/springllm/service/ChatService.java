package com.springllm.service;

import com.springllm.annotations.LargeLanguageModelProxy;
import com.springllm.annotations.Prompt;
import java.lang.reflect.Method;

public interface ChatService {

  Object handlePrompt(
      LargeLanguageModelProxy llmBeanAnnotation,
      Prompt promptAnnotation,
      Method method,
      Object[] args);
}
