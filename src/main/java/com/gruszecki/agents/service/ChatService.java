package com.gruszecki.agents.service;

import com.gruszecki.agents.annotations.LargeLanguageModelProxy;
import com.gruszecki.agents.annotations.Prompt;
import java.lang.reflect.Method;

public interface ChatService {

  Object handlePrompt(
      LargeLanguageModelProxy llmBeanAnnotation,
      Prompt promptAnnotation,
      Method method,
      Object[] args);

  String getSupportedApi();
}
