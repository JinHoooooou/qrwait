import axios from 'axios'
import useOwnerStore from '../store/ownerStore'

const ownerClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

ownerClient.interceptors.request.use((config) => {
  const token = useOwnerStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let isRefreshing = false
let failedQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach(({resolve, reject}) => {
    if (error) reject(error)
    else resolve(token!)
  })
  failedQueue = []
}

ownerClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config

      if (error.response?.status === 401 && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({resolve, reject})
          }).then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            return ownerClient(originalRequest)
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const res = await axios.post('/api/auth/refresh', {}, {withCredentials: true})
          const newToken = res.data.accessToken
          useOwnerStore.getState().setAccessToken(newToken)
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return ownerClient(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          useOwnerStore.getState().clearAuth()
          window.location.href = '/owner/login'
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      const message = error.response?.data?.message ?? '요청 처리 중 오류가 발생했습니다.'
      const err = new Error(message) as Error & { status?: number }
      err.status = error.response?.status
      return Promise.reject(err)
    }
)

export default ownerClient
