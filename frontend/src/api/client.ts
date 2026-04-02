import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
        error.response?.data?.message ?? '요청 처리 중 오류가 발생했습니다.'
    const err = new Error(message) as Error & { status?: number }
    err.status = error.response?.status
    return Promise.reject(err)
  }
)

export default client
