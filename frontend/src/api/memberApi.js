const API_BASE = '/api/public/members'

export async function signup({name, email, password, confirmPassword}) {
    const response = await fetch(`${API_BASE}/signup`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({name, email, password, confirmPassword}),
    })
    const data = await response.json()
    if (!response.ok) {
        throw data
    }
    return data
}

export async function signin({email, password}) {
    const response = await fetch(`${API_BASE}/signin`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({email, password}),
    })
    const data = await response.json()
    if (!response.ok) {
        throw data
    }
    return data
}
