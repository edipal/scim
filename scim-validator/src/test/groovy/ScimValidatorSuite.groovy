

import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import specs.A1_ServiceDiscoverySpec
import specs.A2_SchemaValidationSpec
import specs.A3_UserCrudSpec
import specs.A4_PatchOperationsSpec
import specs.A5_FilteringSpec
import specs.A5_PaginationSpec
import specs.A5_SortingSpec
import specs.A6_GroupLifecycleSpec
import specs.A7_BulkOperationsSpec
import specs.A8_SecurityAndRobustnessSpec
import specs.A9_NegativeAndEdgeCasesSpec

@Suite
@SelectClasses([
    A1_ServiceDiscoverySpec.class,
    A2_SchemaValidationSpec.class,
    A3_UserCrudSpec.class,
    A4_PatchOperationsSpec.class,
    A5_FilteringSpec.class,
    A5_PaginationSpec.class,
    A5_SortingSpec.class,
    A6_GroupLifecycleSpec.class,
    A7_BulkOperationsSpec.class,
    A8_SecurityAndRobustnessSpec.class,
    A9_NegativeAndEdgeCasesSpec.class
])
class ScimValidatorSuite {
}
