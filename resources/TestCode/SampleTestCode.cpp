#include "CppUTest/TestHarness.h"
#include <SampleTarget.h>

TEST_GROUP(SampleTestGroup)
{
    void setup() {}
    void teardown() {}
};

TEST(SampleTestGroup, AddTest1)
{
    // Add(1, 2)が3になることを確認
    CHECK_EQUAL(Add(1, 2), 3);
}

TEST(SampleTestGroup, AddTest2)
{
    struct ADD_TEST_DATA_T {
        int32_t a;
        int32_t b;
        int32_t expected;
    };
    ADD_TEST_DATA_T testDatas[] = {
        // 1 + 1 = 2
        {1, 1, 2},
        // 3 + 5 = 8
        {3, 5, 8},
        // 100 + 1000 = 1100
        {100, 1000, 1100},
        // -100 + 12345 = 12245
        {-100, 12345, 12245},
    };
    size_t testDatasSize = sizeof(testDatas) / sizeof(testDatas[0]);
    for (int32_t i = 0; i < testDatasSize; ++i) {
        int32_t actual = Add(testDatas[i].a, testDatas[i].b);
        CHECK_EQUAL(testDatas[i].expected, actual);
    }
}