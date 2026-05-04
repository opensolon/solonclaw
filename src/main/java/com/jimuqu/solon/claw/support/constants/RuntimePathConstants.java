package com.jimuqu.solon.claw.support.constants;

/** 运行时目录与默认值常量。 */
public interface RuntimePathConstants {
    String RUNTIME_HOME = "runtime";
    String CONTEXT_DIR_NAME = "context";
    String SKILLS_DIR_NAME = "skills";
    String CACHE_DIR_NAME = "cache";
    String DATA_DIR_NAME = "data";
    String CONFIG_FILE_NAME = "config.yml";
    String CONFIG_EXAMPLE_FILE_NAME = "config.example.yml";
    String STATE_DB_FILE_NAME = "state.db";
    String LOGS_DIR_NAME = "logs";
    String CONTEXT_DIR = RUNTIME_HOME + "/" + CONTEXT_DIR_NAME;
    String SKILLS_DIR = RUNTIME_HOME + "/" + SKILLS_DIR_NAME;
    String CACHE_DIR = RUNTIME_HOME + "/" + CACHE_DIR_NAME;
    String STATE_DB = RUNTIME_HOME + "/" + DATA_DIR_NAME + "/" + STATE_DB_FILE_NAME;
    String CONFIG_FILE = RUNTIME_HOME + "/" + CONFIG_FILE_NAME;
    String LOGS_DIR = RUNTIME_HOME + "/" + LOGS_DIR_NAME;

    String DEFAULT_PROVIDER_KEY = "default";
    String DEFAULT_LLM_PROVIDER = "openai";
    String DEFAULT_LLM_API_URL = "https://api.openai.com";
    String DEFAULT_LLM_MODEL = "gpt-5.4";
    String DEFAULT_REASONING_EFFORT = "medium";
    int DEFAULT_CONTEXT_WINDOW_TOKENS = 128000;

    int DEFAULT_SCHEDULER_TICK_SECONDS = 60;
    int DEFAULT_MAX_TOKENS = 4096;
    double DEFAULT_TEMPERATURE = 0.2D;

    int DEFAULT_HEARTBEAT_INTERVAL_MINUTES = 15;
}
