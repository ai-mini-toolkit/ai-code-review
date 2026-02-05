#!/bin/bash

################################################################################
# AI Code Review - PoC 测试执行脚本
# 一键运行所有 PoC 测试并生成报告
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "================================================================================"
echo "AI Code Review - PoC 测试套件"
echo "================================================================================"
echo ""

# Parse arguments
RUN_JAVAPARSER=true
RUN_CODECOMMIT=false  # Default to false (needs AWS setup)
RUN_REDIS=true
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-javaparser)
            RUN_JAVAPARSER=false
            shift
            ;;
        --run-codecommit)
            RUN_CODECOMMIT=true
            shift
            ;;
        --skip-redis)
            RUN_REDIS=false
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --all)
            RUN_JAVAPARSER=true
            RUN_CODECOMMIT=true
            RUN_REDIS=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-javaparser    跳过 JavaParser 测试"
            echo "  --run-codecommit     运行 AWS CodeCommit 测试 (需要 AWS 配置)"
            echo "  --skip-redis         跳过 Redis 测试"
            echo "  --skip-build         跳过 Maven 编译步骤"
            echo "  --all                运行所有测试"
            echo "  --help               显示此帮助信息"
            echo ""
            echo "Examples:"
            echo "  $0                              # 运行 JavaParser 和 Redis 测试"
            echo "  $0 --all                        # 运行所有测试"
            echo "  $0 --run-codecommit             # 包含 CodeCommit 测试"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check prerequisites
echo "检查前置条件..."
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到 Java${NC}"
    echo "请安装 Java 17 或更高版本"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}错误: Java 版本过低 (需要 17+)${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java version: $(java -version 2>&1 | head -n 1)"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误: 未找到 Maven${NC}"
    echo "请安装 Maven 3.6 或更高版本"
    exit 1
fi
echo -e "${GREEN}✓${NC} Maven version: $(mvn -version | head -n 1)"

# Check Redis if needed
if [ "$RUN_REDIS" = true ]; then
    if ! command -v redis-cli &> /dev/null && ! command -v docker &> /dev/null; then
        echo -e "${YELLOW}警告: 未找到 redis-cli 或 docker${NC}"
        echo "Redis 测试需要 Redis 服务器"
        echo "可以运行: docker run -d -p 6379:6379 redis:latest"
        read -p "是否继续 Redis 测试? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            RUN_REDIS=false
        fi
    else
        echo -e "${GREEN}✓${NC} Redis client available"
    fi
fi

echo ""
echo "================================================================================"
echo "测试计划"
echo "================================================================================"
echo ""
echo "将运行以下测试:"
[ "$RUN_JAVAPARSER" = true ] && echo -e "  ${GREEN}✓${NC} JavaParser 性能测试"
[ "$RUN_CODECOMMIT" = true ] && echo -e "  ${GREEN}✓${NC} AWS CodeCommit 集成测试" || echo -e "  ${YELLOW}○${NC} AWS CodeCommit 集成测试 (跳过)"
[ "$RUN_REDIS" = true ] && echo -e "  ${GREEN}✓${NC} Redis 队列并发测试"
echo ""

read -p "按 Enter 继续，或 Ctrl+C 取消..."
echo ""

# Create results directory
RESULTS_DIR="$PROJECT_ROOT/_bmad-output/poc-test-results"
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
SUMMARY_FILE="$RESULTS_DIR/test-summary-$TIMESTAMP.txt"

# Initialize summary
echo "AI Code Review - PoC 测试总结" > "$SUMMARY_FILE"
echo "执行时间: $(date)" >> "$SUMMARY_FILE"
echo "================================================================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Track results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

################################################################################
# Test 1: JavaParser Performance
################################################################################

if [ "$RUN_JAVAPARSER" = true ]; then
    echo "================================================================================"
    echo "PoC 1: JavaParser 性能测试"
    echo "================================================================================"
    echo ""

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    cd "$SCRIPT_DIR/javaparser-performance"

    if [ "$SKIP_BUILD" = false ]; then
        echo "编译项目..."
        if mvn clean compile > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} 编译成功"
        else
            echo -e "${RED}✗${NC} 编译失败"
            echo "JavaParser: FAILED (编译失败)" >> "$SUMMARY_FILE"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            continue
        fi
    fi

    echo "运行测试..."
    if mvn exec:java -Dexec.mainClass="com.aicr.poc.JavaParserPerformanceTest" 2>&1 | tee "$RESULTS_DIR/javaparser-output-$TIMESTAMP.log"; then
        echo -e "${GREEN}✓${NC} JavaParser 测试完成"

        # Check if report exists
        if [ -f "target/javaparser-performance-report.json" ]; then
            cp "target/javaparser-performance-report.json" "$RESULTS_DIR/javaparser-report-$TIMESTAMP.json"
            echo "JavaParser: PASSED" >> "$SUMMARY_FILE"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo "JavaParser: COMPLETED (无报告文件)" >> "$SUMMARY_FILE"
        fi
    else
        echo -e "${RED}✗${NC} JavaParser 测试失败"
        echo "JavaParser: FAILED" >> "$SUMMARY_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    echo ""
    cd "$SCRIPT_DIR"
fi

################################################################################
# Test 2: AWS CodeCommit Integration
################################################################################

if [ "$RUN_CODECOMMIT" = true ]; then
    echo "================================================================================"
    echo "PoC 2: AWS CodeCommit 集成测试"
    echo "================================================================================"
    echo ""

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    cd "$SCRIPT_DIR/aws-codecommit"

    # Check AWS configuration
    if [ -z "$TEST_REPOSITORY" ] || [ -z "$TEST_BEFORE_COMMIT" ] || [ -z "$TEST_AFTER_COMMIT" ]; then
        echo -e "${YELLOW}警告: 缺少 AWS CodeCommit 配置${NC}"
        echo "需要设置以下环境变量:"
        echo "  export TEST_REPOSITORY=your-repo"
        echo "  export TEST_BEFORE_COMMIT=commit-id-1"
        echo "  export TEST_AFTER_COMMIT=commit-id-2"
        echo ""
        echo "运行 Demo 模式..."
    fi

    if [ "$SKIP_BUILD" = false ]; then
        echo "编译项目..."
        if mvn clean compile > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} 编译成功"
        else
            echo -e "${RED}✗${NC} 编译失败"
            echo "AWS CodeCommit: FAILED (编译失败)" >> "$SUMMARY_FILE"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            continue
        fi
    fi

    echo "运行测试..."
    if mvn exec:java -Dexec.mainClass="com.aicr.poc.AwsCodeCommitIntegrationTest" 2>&1 | tee "$RESULTS_DIR/codecommit-output-$TIMESTAMP.log"; then
        echo -e "${GREEN}✓${NC} AWS CodeCommit 测试完成"

        if [ -f "target/codecommit-integration-report.json" ]; then
            cp "target/codecommit-integration-report.json" "$RESULTS_DIR/codecommit-report-$TIMESTAMP.json"
            echo "AWS CodeCommit: PASSED" >> "$SUMMARY_FILE"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo "AWS CodeCommit: COMPLETED (Demo 模式)" >> "$SUMMARY_FILE"
        fi
    else
        echo -e "${RED}✗${NC} AWS CodeCommit 测试失败"
        echo "AWS CodeCommit: FAILED" >> "$SUMMARY_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    echo ""
    cd "$SCRIPT_DIR"
fi

################################################################################
# Test 3: Redis Queue Concurrency
################################################################################

if [ "$RUN_REDIS" = true ]; then
    echo "================================================================================"
    echo "PoC 3: Redis 队列并发测试"
    echo "================================================================================"
    echo ""

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    # Check Redis
    REDIS_RUNNING=false
    if redis-cli ping > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Redis 已运行"
        REDIS_RUNNING=true
    elif command -v docker &> /dev/null; then
        echo "启动 Redis Docker 容器..."
        if docker run -d --name redis-poc-test -p 6379:6379 redis:latest > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} Redis 容器已启动"
            sleep 3  # Wait for Redis to start
            REDIS_RUNNING=true
            REDIS_CLEANUP=true
        elif docker start redis-poc-test > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} Redis 容器已启动"
            sleep 2
            REDIS_RUNNING=true
            REDIS_CLEANUP=true
        else
            echo -e "${RED}✗${NC} 无法启动 Redis"
        fi
    fi

    if [ "$REDIS_RUNNING" = false ]; then
        echo -e "${RED}错误: Redis 未运行${NC}"
        echo "请先启动 Redis:"
        echo "  docker run -d -p 6379:6379 redis:latest"
        echo "Redis: SKIPPED (Redis 未运行)" >> "$SUMMARY_FILE"
        cd "$SCRIPT_DIR"
        continue
    fi

    cd "$SCRIPT_DIR/redis-queue"

    if [ "$SKIP_BUILD" = false ]; then
        echo "编译项目..."
        if mvn clean compile > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} 编译成功"
        else
            echo -e "${RED}✗${NC} 编译失败"
            echo "Redis: FAILED (编译失败)" >> "$SUMMARY_FILE"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            continue
        fi
    fi

    echo "运行测试..."
    if mvn exec:java -Dexec.mainClass="com.aicr.poc.RedisQueuePerformanceTest" 2>&1 | tee "$RESULTS_DIR/redis-output-$TIMESTAMP.log"; then
        echo -e "${GREEN}✓${NC} Redis 测试完成"

        if [ -f "target/redis-queue-report.json" ]; then
            cp "target/redis-queue-report.json" "$RESULTS_DIR/redis-report-$TIMESTAMP.json"
            echo "Redis Queue: PASSED" >> "$SUMMARY_FILE"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo "Redis Queue: COMPLETED (无报告文件)" >> "$SUMMARY_FILE"
        fi
    else
        echo -e "${RED}✗${NC} Redis 测试失败"
        echo "Redis Queue: FAILED" >> "$SUMMARY_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Cleanup Redis container if we started it
    if [ "$REDIS_CLEANUP" = true ]; then
        echo "清理 Redis 容器..."
        docker stop redis-poc-test > /dev/null 2>&1
        docker rm redis-poc-test > /dev/null 2>&1
    fi

    echo ""
    cd "$SCRIPT_DIR"
fi

################################################################################
# Generate Summary
################################################################################

echo "================================================================================"
echo "测试总结"
echo "================================================================================"
echo ""

echo "" >> "$SUMMARY_FILE"
echo "================================================================================" >> "$SUMMARY_FILE"
echo "总结" >> "$SUMMARY_FILE"
echo "================================================================================" >> "$SUMMARY_FILE"
echo "总测试数: $TOTAL_TESTS" >> "$SUMMARY_FILE"
echo "通过: $PASSED_TESTS" >> "$SUMMARY_FILE"
echo "失败: $FAILED_TESTS" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过! ($PASSED_TESTS/$TOTAL_TESTS)${NC}"
    echo "整体决策: GO" >> "$SUMMARY_FILE"
    echo ""
    echo "下一步:"
    echo "  1. 查看详细报告: $RESULTS_DIR/"
    echo "  2. 填写 PoC 执行报告: _bmad-output/implementation-artifacts/poc-execution-report.md"
    echo "  3. 开始主项目集成"
else
    echo -e "${RED}部分测试失败 ($PASSED_TESTS/$TOTAL_TESTS 通过)${NC}"
    echo "整体决策: NO-GO / GO with Caution" >> "$SUMMARY_FILE"
    echo ""
    echo "请检查失败的测试并采取措施"
fi

echo "" >> "$SUMMARY_FILE"
echo "结果文件位置: $RESULTS_DIR/" >> "$SUMMARY_FILE"

cat "$SUMMARY_FILE"
echo ""
echo "详细结果已保存到: $SUMMARY_FILE"
echo ""

exit $([ $FAILED_TESTS -eq 0 ] && echo 0 || echo 1)
