import {useNavigate} from 'react-router-dom'

function HomePage() {
    const navigate = useNavigate()
    const token = localStorage.getItem('accessToken')

    const handleLogout = () => {
        localStorage.removeItem('accessToken')
        navigate('/signin')
    }

    if (!token) {
        navigate('/signin')
        return null
    }

    return (
        <div className="auth-container">
            <div className="auth-card">
                <h1>Coupon System</h1>
                <p>쿠폰 관리 시스템에 오신 것을 환영합니다.</p>
                <button className="auth-button logout-button" onClick={handleLogout}>
                    로그아웃
                </button>
            </div>
        </div>
    )
}

export default HomePage
