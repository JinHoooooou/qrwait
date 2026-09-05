import {useEffect, useRef} from 'react'

const MAX_RETRIES = 3

export type SseConnectionStatus = 'connecting' | 'connected' | 'error'

interface UseWaitingSseOptions {
  enabled: boolean
  onUpdated?: () => void
  onCalled?: () => void
  onPostponed?: () => void
  onConnectionChange?: (status: SseConnectionStatus) => void
}

export function useWaitingSse(
    waitingId: string | undefined,
    storeId: string | null,
    options: UseWaitingSseOptions,
): void {
  const {enabled} = options
  const onUpdatedRef = useRef(options.onUpdated)
  const onCalledRef = useRef(options.onCalled)
  const onPostponedRef = useRef(options.onPostponed)
  const onConnectionChangeRef = useRef(options.onConnectionChange)

  onUpdatedRef.current = options.onUpdated
  onCalledRef.current = options.onCalled
  onPostponedRef.current = options.onPostponed
  onConnectionChangeRef.current = options.onConnectionChange

  useEffect(() => {
    if (!enabled || !waitingId || !storeId) return

    let unmounted = false
    let retryCount = 0
    let retryTimer: ReturnType<typeof setTimeout> | null = null
    let es: EventSource | null = null

    const connect = () => {
      if (unmounted) return

      es = new EventSource(`/api/waitings/${waitingId}/stream?storeId=${storeId}`)

      es.onopen = () => {
        if (unmounted) return
        onConnectionChangeRef.current?.('connected')
        retryCount = 0
      }

      es.onerror = () => {
        if (unmounted) return
        es?.close()
        if (retryCount < MAX_RETRIES) {
          retryCount++
          onConnectionChangeRef.current?.('connecting')
          retryTimer = setTimeout(connect, 3000)
        } else {
          onConnectionChangeRef.current?.('error')
        }
      }

      es.addEventListener('waiting-updated', () => {
        if (!unmounted) onUpdatedRef.current?.()
      })

      es.addEventListener('waiting-called', (e) => {
        if (unmounted) return
        try {
          const data = JSON.parse((e as MessageEvent).data)
          if (data.waitingId === waitingId) onCalledRef.current?.()
        } catch {
          // payload 파싱 실패 시 무시
        }
      })

      es.addEventListener('waiting-postponed', (e) => {
        if (unmounted) return
        try {
          const data = JSON.parse((e as MessageEvent).data)
          if (data.waitingId === waitingId) onPostponedRef.current?.()
        } catch {
          // payload 파싱 실패 시 무시
        }
      })
    }

    onConnectionChangeRef.current?.('connecting')
    connect()

    return () => {
      unmounted = true
      if (retryTimer) clearTimeout(retryTimer)
      es?.close()
    }
  }, [waitingId, storeId, enabled])
}
