package dev.common.exception;

// 비즈니스 예외의 부모 클래스
public class ServiceException extends RuntimeException {

    private final CommonError commonError;

    public ServiceException(CommonError commonError) {
        super(commonError.getMessage());
        this.commonError = commonError;
    }

    /**
     * 근본 원인(cause)을 함께 포장할 때 사용 (배치 등에서 OOM·DB 오류 래핑)
     */
    public ServiceException(CommonError commonError, Throwable cause) {
        super(commonError.getMessage(), cause);
        this.commonError = commonError;
    }

    public CommonError getCommonError() {
        return commonError;
    }
}
