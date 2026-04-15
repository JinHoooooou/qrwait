import client from './client'

export type StoreStatus = 'OPEN' | 'BREAK' | 'FULL' | 'CLOSED'

export interface StoreResponse {
  storeId: string
  name: string
  status: StoreStatus
}

export interface RegisterWaitingRequest {
  phoneNumber: string
  partySize: number
}

export interface StoreWaitingStatus {
  totalWaiting: number
  estimatedWaitMinutes: number
}

export interface RegisterWaitingResponse {
  waitingId: string
  waitingNumber: number
  currentRank: number
  totalWaiting: number
  estimatedWaitMinutes: number
}

export interface WaitingStatusResponse {
  currentRank: number
  totalWaiting: number
  estimatedWaitMinutes: number
}

export interface CreateStoreRequest {
  name: string
}

export interface CreateStoreResponse {
  storeId: string
  name: string
  qrUrl: string
}

export const getStore = (storeId: string) =>
    client.get<StoreResponse>(`/stores/${storeId}`).then((res) => res.data)

export const createStore = (body: CreateStoreRequest) =>
    client.post<CreateStoreResponse>('/stores', body).then((res) => res.data)

export const getStoreQrUrl = (storeId: string) => `/api/stores/${storeId}/qr`

export const registerWaiting = (storeId: string, body: RegisterWaitingRequest) =>
  client.post<RegisterWaitingResponse>(`/stores/${storeId}/waitings`, body).then((res) => res.data)

export const getStoreWaitingStatus = (storeId: string) =>
    client.get<StoreWaitingStatus>(`/stores/${storeId}/waitings/status`).then((res) => res.data)

export const getWaiting = (waitingId: string) =>
  client.get<WaitingStatusResponse>(`/waitings/${waitingId}`).then((res) => res.data)

export const cancelWaiting = (waitingId: string) =>
  client.delete(`/waitings/${waitingId}`).then((res) => res.data)
