@echo off
REM ============================================================================
REM AI Code Review - PoC 测试执行脚本 (Windows)
REM 一键运行所有 PoC 测试并生成报告
REM ============================================================================

setlocal enabledelayedexpansion

echo ================================================================================
echo AI Code Review - PoC 测试套件
echo ================================================================================
echo.

REM Get script directory
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..\..

REM Default settings
set RUN_JAVAPARSER=true
set RUN_CODECOMMIT=false
set RUN_REDIS=true
set SKIP_BUILD=false

REM Parse arguments
:parse_args
if "%1"=="" goto check_prereqs
if "%1"=="--skip-javaparser" (
    set RUN_JAVAPARSER=false
    shift
    goto parse_args
)
if "%1"=="--run-codecommit" (
    set RUN_CODECOMMIT=true
    shift
    goto parse_args
)
if "%1"=="--skip-redis" (
    set RUN_REDIS=false
    shift
    goto parse_args
)
if "%1"=="--skip-build" (
    set SKIP_BUILD=true
    shift
    goto parse_args
)
if "%1"=="--all" (
    set RUN_JAVAPARSER=true
    set RUN_CODECOMMIT=true
    set RUN_REDIS=true
    shift
    goto parse_args
)
if "%1"=="--help" (
    echo Usage: %0 [options]
    echo.
    echo Options:
    echo   --skip-javaparser    跳过 JavaParser 测试
    echo   --run-codecommit     运行 AWS CodeCommit 测试 (需要 AWS 配置)
    echo   --skip-redis         跳过 Redis 测试
    echo   --skip-build         跳过 Maven 编译步骤
    echo   --all                运行所有测试
    echo   --help               显示此帮助信息
    echo.
    echo Examples:
    echo   %0                           # 运行 JavaParser 和 Redis 测试
    echo   %0 --all                     # 运行所有测试
    echo   %0 --run-codecommit          # 包含 CodeCommit 测试
    exit /b 0
)
echo Unknown option: %1
echo Use --help for usage information
exit /b 1

:check_prereqs
echo 检查前置条件...
echo.

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java
    echo 请安装 Java 17 或更高版本
    exit /b 1
)
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
echo [✓] Java version: %JAVA_VERSION%

REM Check Maven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Maven
    echo 请安装 Maven 3.6 或更高版本
    exit /b 1
)
for /f "tokens=3" %%g in ('mvn -version 2^>^&1 ^| findstr "Apache Maven"') do (
    set MAVEN_VERSION=%%g
    goto maven_found
)
:maven_found
echo [✓] Maven version: %MAVEN_VERSION%

REM Check Redis if needed
if "%RUN_REDIS%"=="true" (
    redis-cli ping >nul 2>&1
    if errorlevel 1 (
        docker --version >nul 2>&1
        if errorlevel 1 (
            echo [警告] 未找到 redis-cli 或 docker
            echo Redis 测试需要 Redis 服务器
            echo 可以运行: docker run -d -p 6379:6379 redis:latest
            set /p CONTINUE="是否继续 Redis 测试? (y/n) "
            if /i not "!CONTINUE!"=="y" set RUN_REDIS=false
        ) else (
            echo [✓] Docker available
        )
    ) else (
        echo [✓] Redis available
    )
)

echo.
echo ================================================================================
echo 测试计划
echo ================================================================================
echo.
echo 将运行以下测试:
if "%RUN_JAVAPARSER%"=="true" echo   [✓] JavaParser 性能测试
if "%RUN_CODECOMMIT%"=="true" (echo   [✓] AWS CodeCommit 集成测试) else (echo   [○] AWS CodeCommit 集成测试 (跳过))
if "%RUN_REDIS%"=="true" echo   [✓] Redis 队列并发测试
echo.

pause
echo.

REM Create results directory
set RESULTS_DIR=%PROJECT_ROOT%\_bmad-output\poc-test-results
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set SUMMARY_FILE=%RESULTS_DIR%\test-summary-%TIMESTAMP%.txt

REM Initialize summary
echo AI Code Review - PoC 测试总结 > "%SUMMARY_FILE%"
echo 执行时间: %date% %time% >> "%SUMMARY_FILE%"
echo ================================================================================ >> "%SUMMARY_FILE%"
echo. >> "%SUMMARY_FILE%"

set TOTAL_TESTS=0
set PASSED_TESTS=0
set FAILED_TESTS=0

REM ============================================================================
REM Test 1: JavaParser Performance
REM ============================================================================

if "%RUN_JAVAPARSER%"=="true" (
    echo ================================================================================
    echo PoC 1: JavaParser 性能测试
    echo ================================================================================
    echo.

    set /a TOTAL_TESTS+=1

    cd /d "%SCRIPT_DIR%javaparser-performance"

    if "%SKIP_BUILD%"=="false" (
        echo 编译项目...
        call mvn clean compile >nul 2>&1
        if errorlevel 1 (
            echo [✗] 编译失败
            echo JavaParser: FAILED (编译失败) >> "%SUMMARY_FILE%"
            set /a FAILED_TESTS+=1
            goto test_codecommit
        )
        echo [✓] 编译成功
    )

    echo 运行测试...
    call mvn exec:java -Dexec.mainClass="com.aicr.poc.JavaParserPerformanceTest" > "%RESULTS_DIR%\javaparser-output-%TIMESTAMP%.log" 2>&1
    if errorlevel 1 (
        echo [✗] JavaParser 测试失败
        echo JavaParser: FAILED >> "%SUMMARY_FILE%"
        set /a FAILED_TESTS+=1
    ) else (
        echo [✓] JavaParser 测试完成

        if exist "target\javaparser-performance-report.json" (
            copy /y "target\javaparser-performance-report.json" "%RESULTS_DIR%\javaparser-report-%TIMESTAMP%.json" >nul
            echo JavaParser: PASSED >> "%SUMMARY_FILE%"
            set /a PASSED_TESTS+=1
        ) else (
            echo JavaParser: COMPLETED (无报告文件) >> "%SUMMARY_FILE%"
        )
    )

    echo.
    cd /d "%SCRIPT_DIR%"
)

:test_codecommit
REM ============================================================================
REM Test 2: AWS CodeCommit Integration
REM ============================================================================

if "%RUN_CODECOMMIT%"=="true" (
    echo ================================================================================
    echo PoC 2: AWS CodeCommit 集成测试
    echo ================================================================================
    echo.

    set /a TOTAL_TESTS+=1

    cd /d "%SCRIPT_DIR%aws-codecommit"

    if "%TEST_REPOSITORY%"=="" (
        echo [警告] 缺少 AWS CodeCommit 配置
        echo 需要设置以下环境变量:
        echo   set TEST_REPOSITORY=your-repo
        echo   set TEST_BEFORE_COMMIT=commit-id-1
        echo   set TEST_AFTER_COMMIT=commit-id-2
        echo.
        echo 运行 Demo 模式...
    )

    if "%SKIP_BUILD%"=="false" (
        echo 编译项目...
        call mvn clean compile >nul 2>&1
        if errorlevel 1 (
            echo [✗] 编译失败
            echo AWS CodeCommit: FAILED (编译失败) >> "%SUMMARY_FILE%"
            set /a FAILED_TESTS+=1
            goto test_redis
        )
        echo [✓] 编译成功
    )

    echo 运行测试...
    call mvn exec:java -Dexec.mainClass="com.aicr.poc.AwsCodeCommitIntegrationTest" > "%RESULTS_DIR%\codecommit-output-%TIMESTAMP%.log" 2>&1
    if errorlevel 1 (
        echo [✗] AWS CodeCommit 测试失败
        echo AWS CodeCommit: FAILED >> "%SUMMARY_FILE%"
        set /a FAILED_TESTS+=1
    ) else (
        echo [✓] AWS CodeCommit 测试完成

        if exist "target\codecommit-integration-report.json" (
            copy /y "target\codecommit-integration-report.json" "%RESULTS_DIR%\codecommit-report-%TIMESTAMP%.json" >nul
            echo AWS CodeCommit: PASSED >> "%SUMMARY_FILE%"
            set /a PASSED_TESTS+=1
        ) else (
            echo AWS CodeCommit: COMPLETED (Demo 模式) >> "%SUMMARY_FILE%"
        )
    )

    echo.
    cd /d "%SCRIPT_DIR%"
)

:test_redis
REM ============================================================================
REM Test 3: Redis Queue Concurrency
REM ============================================================================

if "%RUN_REDIS%"=="true" (
    echo ================================================================================
    echo PoC 3: Redis 队列并发测试
    echo ================================================================================
    echo.

    set /a TOTAL_TESTS+=1

    REM Check Redis
    set REDIS_RUNNING=false
    redis-cli ping >nul 2>&1
    if not errorlevel 1 (
        echo [✓] Redis 已运行
        set REDIS_RUNNING=true
    ) else (
        docker --version >nul 2>&1
        if not errorlevel 1 (
            echo 启动 Redis Docker 容器...
            docker run -d --name redis-poc-test -p 6379:6379 redis:latest >nul 2>&1
            if not errorlevel 1 (
                echo [✓] Redis 容器已启动
                timeout /t 3 /nobreak >nul
                set REDIS_RUNNING=true
                set REDIS_CLEANUP=true
            ) else (
                docker start redis-poc-test >nul 2>&1
                if not errorlevel 1 (
                    echo [✓] Redis 容器已启动
                    timeout /t 2 /nobreak >nul
                    set REDIS_RUNNING=true
                    set REDIS_CLEANUP=true
                )
            )
        )
    )

    if "%REDIS_RUNNING%"=="false" (
        echo [错误] Redis 未运行
        echo 请先启动 Redis:
        echo   docker run -d -p 6379:6379 redis:latest
        echo Redis: SKIPPED (Redis 未运行) >> "%SUMMARY_FILE%"
        goto summary
    )

    cd /d "%SCRIPT_DIR%redis-queue"

    if "%SKIP_BUILD%"=="false" (
        echo 编译项目...
        call mvn clean compile >nul 2>&1
        if errorlevel 1 (
            echo [✗] 编译失败
            echo Redis: FAILED (编译失败) >> "%SUMMARY_FILE%"
            set /a FAILED_TESTS+=1
            goto redis_cleanup
        )
        echo [✓] 编译成功
    )

    echo 运行测试...
    call mvn exec:java -Dexec.mainClass="com.aicr.poc.RedisQueuePerformanceTest" > "%RESULTS_DIR%\redis-output-%TIMESTAMP%.log" 2>&1
    if errorlevel 1 (
        echo [✗] Redis 测试失败
        echo Redis Queue: FAILED >> "%SUMMARY_FILE%"
        set /a FAILED_TESTS+=1
    ) else (
        echo [✓] Redis 测试完成

        if exist "target\redis-queue-report.json" (
            copy /y "target\redis-queue-report.json" "%RESULTS_DIR%\redis-report-%TIMESTAMP%.json" >nul
            echo Redis Queue: PASSED >> "%SUMMARY_FILE%"
            set /a PASSED_TESTS+=1
        ) else (
            echo Redis Queue: COMPLETED (无报告文件) >> "%SUMMARY_FILE%"
        )
    )

    :redis_cleanup
    if "%REDIS_CLEANUP%"=="true" (
        echo 清理 Redis 容器...
        docker stop redis-poc-test >nul 2>&1
        docker rm redis-poc-test >nul 2>&1
    )

    echo.
    cd /d "%SCRIPT_DIR%"
)

:summary
REM ============================================================================
REM Generate Summary
REM ============================================================================

echo ================================================================================
echo 测试总结
echo ================================================================================
echo.

echo. >> "%SUMMARY_FILE%"
echo ================================================================================ >> "%SUMMARY_FILE%"
echo 总结 >> "%SUMMARY_FILE%"
echo ================================================================================ >> "%SUMMARY_FILE%"
echo 总测试数: %TOTAL_TESTS% >> "%SUMMARY_FILE%"
echo 通过: %PASSED_TESTS% >> "%SUMMARY_FILE%"
echo 失败: %FAILED_TESTS% >> "%SUMMARY_FILE%"
echo. >> "%SUMMARY_FILE%"

if %FAILED_TESTS% EQU 0 (
    echo [成功] 所有测试通过! (%PASSED_TESTS%/%TOTAL_TESTS%)
    echo 整体决策: GO >> "%SUMMARY_FILE%"
    echo.
    echo 下一步:
    echo   1. 查看详细报告: %RESULTS_DIR%\
    echo   2. 填写 PoC 执行报告: _bmad-output\implementation-artifacts\poc-execution-report.md
    echo   3. 开始主项目集成
    set EXIT_CODE=0
) else (
    echo [失败] 部分测试失败 (%PASSED_TESTS%/%TOTAL_TESTS% 通过)
    echo 整体决策: NO-GO / GO with Caution >> "%SUMMARY_FILE%"
    echo.
    echo 请检查失败的测试并采取措施
    set EXIT_CODE=1
)

echo. >> "%SUMMARY_FILE%"
echo 结果文件位置: %RESULTS_DIR%\ >> "%SUMMARY_FILE%"

type "%SUMMARY_FILE%"
echo.
echo 详细结果已保存到: %SUMMARY_FILE%
echo.

pause
exit /b %EXIT_CODE%
