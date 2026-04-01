import client from './client'

export interface StoreResponse {
  storeId: string
  name: string
}

export interface RegisterWaitingRequest {
  visitorName: string
  partySize: number
}

export interface RegisterWaitingResponse {
  waitingId: string
  waitingNumber: number
  currentRank: number
  totalWaiting: number
  estimatedWaitMinutes: number
  waitingToken: string
}

export interface WaitingStatusResponse {
  currentRank: number
  totalWaiting: number
  estimatedWaitMinutes: number
}

export const getStore = (storeId: string) =>
    client.get<StoreResponse>(`/stores/${storeId}`).then((res) => res.data)

export const registerWaiting = (storeId: string, body: RegisterWaitingRequest) =>
  client.post<RegisterWaitingResponse>(`/stores/${storeId}/waitings`, body).then((res) => res.data)

export const getWaiting = (waitingId: string) =>
  client.get<WaitingStatusResponse>(`/waitings/${waitingId}`).then((res) => res.data)

export const cancelWaiting = (waitingId: string) =>
  client.delete(`/waitings/${waitingId}`).then((res) => res.data)
