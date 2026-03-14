#!/bin/bash

# Test execution script for Caroli AI Android app
# Usage: ./scripts/test.sh [options]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ANDROID_DIR="$PROJECT_ROOT/apps/android"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Caroli AI - Test Runner${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

cd "$ANDROID_DIR"

# Parse arguments
RUN_TESTS=false
RUN_LINT=false
RUN_KTLINT=false
RUN_ALL=false

if [ $# -eq 0 ]; then
    RUN_ALL=true
fi

while [[ $# -gt 0 ]]; do
    case $1 in
        --tests|-t)
            RUN_TESTS=true
            shift
            ;;
        --lint|-l)
            RUN_LINT=true
            shift
            ;;
        --ktlint|-k)
            RUN_KTLINT=true
            shift
            ;;
        --all|-a)
            RUN_ALL=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  -t, --tests     Run unit tests"
            echo "  -l, --lint      Run Android lint"
            echo "  -k, --ktlint    Run ktlint check"
            echo "  -a, --all       Run all checks (default)"
            echo "  -h, --help      Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0              # Run all checks"
            echo "  $0 -t           # Run tests only"
            echo "  $0 -t -l        # Run tests and lint"
            echo "  $0 --all        # Run all checks"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Run tests
if [ "$RUN_TESTS" = true ] || [ "$RUN_ALL" = true ]; then
    echo -e "${YELLOW}Running unit tests...${NC}"
    ./gradlew testDevDebugUnitTest --tests "so.lai.recalo.*"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Tests passed${NC}"
    else
        echo -e "${RED}✗ Tests failed${NC}"
        echo "See report at: $ANDROID_DIR/app/build/reports/tests/testDevDebugUnitTest/index.html"
        exit 1
    fi
    echo ""
fi

# Run lint
if [ "$RUN_LINT" = true ] || [ "$RUN_ALL" = true ]; then
    echo -e "${YELLOW}Running lint...${NC}"
    ./gradlew lint
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Lint passed${NC}"
    else
        echo -e "${YELLOW}⚠ Lint found issues${NC}"
        echo "See report at: $ANDROID_DIR/app/build/reports/lint-results.html"
    fi
    echo ""
fi

# Run ktlint
if [ "$RUN_KTLINT" = true ] || [ "$RUN_ALL" = true ]; then
    echo -e "${YELLOW}Running ktlint check...${NC}"
    ./gradlew ktlintCheck
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ktlint passed${NC}"
    else
        echo -e "${YELLOW}⚠ ktlint found style issues${NC}"
    fi
    echo ""
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  All checks completed!${NC}"
echo -e "${GREEN}========================================${NC}"
