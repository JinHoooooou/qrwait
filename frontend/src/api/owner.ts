import axios from 'axios'
import ownerClient from './ownerClient'
import useOwnerStore from '../store/ownerStore'

export interface SignUpRequest {
  email: string
  password: string
  storeName: string
  address: string
}

export interface SignUpResponse {
  ownerId: string
  storeId: string
  qrUrl: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  ownerId: string
  storeId: string
}

export const signUp = (body: SignUpRequest): Promise<SignUpResponse> =>
    axios.post('/api/auth/signup', body).then((res) => res.data)

export const login = (body: LoginRequest): Promise<LoginResponse> =>
    axios.post('/api/auth/login', body, {withCredentials: true}).then((res) => res.data)

export const logout = (): Promise<void> =>
    ownerClient.post('/auth/logout').then(() => {
      useOwnerStore.getState().clearAuth()
    })

export const refreshToken = (): Promise<{ accessToken: string; ownerId: string; storeId: string }> =>
    axios.post('/api/auth/refresh', {}, {withCredentials: true}).then((res) => res.data)

export interface UpdateStoreSettingsRequest {
  tableCount: number
  avgTurnoverMinutes: number
}

export const updateStoreSettings = (body: UpdateStoreSettingsRequest): Promise<void> =>
    ownerClient.put('/owner/stores/me/settings', body).then((res) => res.data)
