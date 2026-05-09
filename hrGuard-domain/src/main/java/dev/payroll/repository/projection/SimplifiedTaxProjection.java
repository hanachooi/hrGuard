package dev.payroll.repository.projection;

public record SimplifiedTaxProjection(
        int salaryMin,
        int salaryMax,
        Integer dep1,
        Integer dep2,
        Integer dep3,
        Integer dep4,
        Integer dep5,
        Integer dep6,
        Integer dep7,
        Integer dep8,
        Integer dep9,
        Integer dep10,
        Integer dep11
) {
    public int getTaxByDependents(int dependents) {
        Integer tax = switch (dependents) {
            case 1 -> dep1;
            case 2 -> dep2;
            case 3 -> dep3;
            case 4 -> dep4;
            case 5 -> dep5;
            case 6 -> dep6;
            case 7 -> dep7;
            case 8 -> dep8;
            case 9 -> dep9;
            case 10 -> dep10;
            default -> dep11;
        };
        return tax != null ? tax : 0;
    }
}
