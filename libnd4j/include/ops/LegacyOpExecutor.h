//
// Created by raver on 5/23/2018.
//

#ifndef LIBND4J_LEGACYOPEXECUTOR_H
#define LIBND4J_LEGACYOPEXECUTOR_H

#include <NDArray.h>
#include <array/LaunchContext.h>
#include <vector>

namespace nd4j {
    /**
     * This class executes Legacy ops by ID.
     * Used in legacy ops wrappers for Graph
     *
     * @tparam T
     */
    template <typename T>
    class LegacyOpExecutor {
    private:
    public:
        // TODO: we must get rid of std::vector here
        static void execScalarOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, T scalar, std::vector<T> &extras);

        static void execTransformOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<T> &extras);


        // SummaryStats operations
        static void execSummaryStatsScalarOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<T> &extras, bool biasCorrected);
        static void execSummaryStatsOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<int> &axis, std::vector<T> &extras, bool biasCorrected);


        // Reduce operations
        static void execReduceScalarOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<T> &extras);
        static void execReduceOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<int> &axis, std::vector<T> &extras);

        // Reduce3 operations
        static void execReduce3ScalarOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *y, NDArray<T> *z, std::vector<T> &extras);
        static void execReduce3Op(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *y, NDArray<T> *z, std::vector<int> &axis, std::vector<T> &extras);

        // IndexReduce operations
        static void execIndexReduceScalarOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<T> &extras);
        static void execIndexReduceOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *z, std::vector<int> &axis, std::vector<T> &extras);

        // Pairwise operations
        static void execPairwiseOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *y, NDArray<T> *z, std::vector<T> &extras);

        // Broadcast operations
        static void execBroadcastOp(nd4j::LaunchContext &ctx, int opNum, NDArray<T> *x, NDArray<T> *y, NDArray<T> *z, std::vector<int> &axis);
    };
}



#endif //LIBND4J_LEGACYOPEXECUTOR_H
