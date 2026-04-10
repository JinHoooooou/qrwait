import {useCallback, useEffect, useRef, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import useOwnerStore from '../store/ownerStore'
import {
  callWaiting,
  type DailySummary,
  enterWaiting,
  getDailySummary,
  getMyStore,
  getWaitingList,
  logout,
  type MyStoreResponse,
  noShowWaiting,
  type OwnerWaitingItem,
  type StoreStatus,
  updateStoreStatus,
} from '../api/owner'

const STATUS_LABELS: Record<StoreStatus, string> = {
  OPEN: '운영 중',
  BREAK: '브레이크타임',
  FULL: '만석',
  CLOSED: '영업 종료',
}

const STATUS_COLORS: Record<StoreStatus, string> = {
  OPEN: '#16a34a',
  BREAK: '#d97706',
  FULL: '#dc2626',
  CLOSED: '#6b7280',
}

interface ConfirmDialog {
  message: string
  onConfirm: () => Promise<void>
}

function DashboardPage() {
  const navigate = useNavigate()
  const accessToken = useOwnerStore((s) => s.accessToken)

  const [store, setStore] = useState<MyStoreResponse | null>(null)
  const [waitingList, setWaitingList] = useState<OwnerWaitingItem[]>([])
  const [summary, setSummary] = useState<DailySummary | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialog | null>(null)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [notifPermission, setNotifPermission] = useState<NotificationPermission | null>(null)
  const [alertBanner, setAlertBanner] = useState<string | null>(null)

  const retryCountRef = useRef(0)
  const retryTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const activeRef = useRef(true)

  const fetchWaitingList = useCallback(async () => {
    const list = await getWaitingList()
    setWaitingList(list)
  }, [])

  const fetchSummary = useCallback(async () => {
    const data = await getDailySummary()
    setSummary(data)
  }, [])

  useEffect(() => {
    if (!('Notification' in window)) return
    if (Notification.permission === 'default') {
      Notification.requestPermission().then(setNotifPermission)
    } else {
      setNotifPermission(Notification.permission)
    }
  }, [])

  useEffect(() => {
    Promise.all([getMyStore(), getWaitingList(), getDailySummary()])
        .then(([storeData, list, summaryData]) => {
          setStore(storeData)
          setWaitingList(list)
          setSummary(summaryData)
        })
        .catch(() => setLoadError('데이터를 불러오지 못했습니다. 새로고침해 주세요.'))
  }, [])

  useEffect(() => {
    if (!accessToken) return
    activeRef.current = true

    const connect = async () => {
      try {
        const res = await fetch('/api/owner/stores/me/dashboard/stream', {
          headers: {Authorization: `Bearer ${accessToken}`},
        })
        if (!res.ok || !res.body) throw new Error('SSE 연결 실패')

        retryCountRef.current = 0
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let eventName = ''

        while (activeRef.current) {
          const {done, value} = await reader.read()
          if (done) break

          buffer += decoder.decode(value, {stream: true})
          const blocks = buffer.split('\n\n')
          buffer = blocks.pop() ?? ''

          for (const block of blocks) {
            for (const line of block.split('\n')) {
              if (line.startsWith('event:')) {
                eventName = line.slice(6).trim()
              } else if (line.startsWith('data:')) {
                if (eventName === 'waiting-registered' || eventName === 'waiting-updated') {
                  fetchWaitingList()
                  fetchSummary()
                } else if (eventName === 'alert-threshold-reached') {
                  if (Notification.permission === 'granted') {
                    new Notification('웨이팅 알림', {body: '대기자 수가 임계값을 초과했습니다.'})
                  } else {
                    setAlertBanner('대기자 수가 임계값을 초과했습니다.')
                  }
                }
                eventName = ''
              }
            }
          }
        }
      } catch {
        if (!activeRef.current) return
        if (retryCountRef.current < 3) {
          retryCountRef.current++
          retryTimeoutRef.current = setTimeout(connect, 3000)
        }
      }
    }

    connect()

    return () => {
      activeRef.current = false
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current)
    }
  }, [accessToken, fetchWaitingList, fetchSummary])

  const handleStatusChange = (status: StoreStatus) => {
    setConfirmDialog({
      message: `매장 상태를 "${STATUS_LABELS[status]}"(으)로 변경할까요?`,
      onConfirm: async () => {
        await updateStoreStatus(status)
        setStore((prev) => prev ? {...prev, status} : prev)
      },
    })
  }

  const handleAction = (
      message: string,
      waitingId: string,
      action: (id: string) => Promise<void>,
  ) => {
    setConfirmDialog({
      message,
      onConfirm: async () => {
        setActionLoading(waitingId)
        await action(waitingId)
        await Promise.all([fetchWaitingList(), fetchSummary()])
        setActionLoading(null)
      },
    })
  }

  const handleLogout = async () => {
    await logout()
    navigate('/owner/login')
  }

  const handleConfirm = async () => {
    if (!confirmDialog) return
    try {
      await confirmDialog.onConfirm()
    } finally {
      setConfirmDialog(null)
    }
  }

  return (
      <div style={styles.container}>
        {/* 헤더 */}
        <div style={styles.header}>
          <div>
            <h1 style={styles.storeName}>{store?.name ?? '대시보드'}</h1>
            {store && (
                <span style={{...styles.statusBadge, backgroundColor: STATUS_COLORS[store.status]}}>
              {STATUS_LABELS[store.status]}
            </span>
            )}
          </div>
          <Button variant="secondary" onClick={handleLogout} style={styles.logoutBtn}>
            로그아웃
          </Button>
        </div>

        {/* 알림 권한 거부 시 인앱 배너 */}
        {alertBanner && (
            <div style={styles.alertBanner}>
              <span>{alertBanner}</span>
              <button style={styles.alertClose} onClick={() => setAlertBanner(null)}>✕</button>
            </div>
        )}

        {/* 알림 권한 거부 안내 */}
        {notifPermission === 'denied' && (
            <div style={styles.notifDeniedBanner}>
              브라우저 알림이 차단되어 있습니다. 임계값 초과 시 화면 상단에 배너로 표시됩니다.
            </div>
        )}

        {loadError && <p style={styles.error}>{loadError}</p>}

        {/* 매장 상태 변경 */}
        <section>
          <p style={styles.sectionTitle}>매장 상태</p>
          <div style={styles.statusGrid}>
            {(Object.keys(STATUS_LABELS) as StoreStatus[]).map((status) => (
                <button
                    key={status}
                    style={{
                      ...styles.statusBtn,
                      backgroundColor: store?.status === status ? STATUS_COLORS[status] : '#f3f4f6',
                      color: store?.status === status ? '#fff' : '#374151',
                    }}
                    onClick={() => handleStatusChange(status)}
                >
                  {STATUS_LABELS[status]}
                </button>
            ))}
          </div>
        </section>

        {/* 오늘 통계 */}
        {summary && (
            <section>
              <p style={styles.sectionTitle}>오늘의 통계</p>
              <div style={styles.summaryGrid}>
                <div style={styles.summaryCard}>
                  <p style={styles.summaryValue}>{summary.totalRegistered}</p>
                  <p style={styles.summaryLabel}>등록</p>
                </div>
                <div style={styles.summaryCard}>
                  <p style={styles.summaryValue}>{summary.totalEntered}</p>
                  <p style={styles.summaryLabel}>입장</p>
                </div>
                <div style={styles.summaryCard}>
                  <p style={styles.summaryValue}>{summary.totalNoShow}</p>
                  <p style={styles.summaryLabel}>노쇼</p>
                </div>
                <div style={styles.summaryCard}>
                  <p style={styles.summaryValue}>{summary.totalCancelled}</p>
                  <p style={styles.summaryLabel}>취소</p>
                </div>
              </div>
            </section>
        )}

        {/* 대기 목록 */}
        <section>
          <p style={styles.sectionTitle}>현재 대기 ({waitingList.length}팀)</p>
          {waitingList.length === 0 ? (
              <p style={styles.emptyText}>현재 대기 중인 손님이 없습니다.</p>
          ) : (
              <div style={styles.waitingList}>
                {waitingList.map((item) => (
                    <div key={item.waitingId} style={styles.waitingCard}>
                      <div style={styles.waitingInfo}>
                        <span style={styles.waitingNumber}>#{item.waitingNumber}</span>
                        <span style={styles.waitingName}>{item.visitorName}</span>
                        <span style={styles.waitingMeta}>{item.partySize}명 · {item.elapsedMinutes}분 경과</span>
                        {item.status === 'CALLED' && (
                            <span style={styles.calledBadge}>호출됨</span>
                        )}
                      </div>
                      <div style={styles.actionButtons}>
                        {item.status === 'WAITING' && (
                            <button
                                style={{...styles.actionBtn, ...styles.callBtn}}
                                disabled={actionLoading === item.waitingId}
                                onClick={() => handleAction(
                                    `#${item.waitingNumber} ${item.visitorName} 손님을 호출할까요?`,
                                    item.waitingId,
                                    callWaiting,
                                )}
                            >
                              호출
                            </button>
                        )}
                        {item.status === 'CALLED' && (
                            <>
                              <button
                                  style={{...styles.actionBtn, ...styles.enterBtn}}
                                  disabled={actionLoading === item.waitingId}
                                  onClick={() => handleAction(
                                      `#${item.waitingNumber} ${item.visitorName} 손님 입장 처리할까요?`,
                                      item.waitingId,
                                      enterWaiting,
                                  )}
                              >
                                입장
                              </button>
                              <button
                                  style={{...styles.actionBtn, ...styles.noshowBtn}}
                                  disabled={actionLoading === item.waitingId}
                                  onClick={() => handleAction(
                                      `#${item.waitingNumber} ${item.visitorName} 손님을 노쇼 처리할까요?`,
                                      item.waitingId,
                                      noShowWaiting,
                                  )}
                              >
                                노쇼
                              </button>
                            </>
                        )}
                      </div>
                    </div>
                ))}
              </div>
          )}
        </section>

        {/* 확인 다이얼로그 */}
        {confirmDialog && (
            <div style={styles.overlay}>
              <div style={styles.dialog}>
                <p style={styles.dialogMessage}>{confirmDialog.message}</p>
                <div style={styles.dialogButtons}>
                  <Button variant="secondary" onClick={() => setConfirmDialog(null)}>취소</Button>
                  <Button onClick={handleConfirm}>확인</Button>
                </div>
              </div>
            </div>
        )}
      </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '1.5rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  storeName: {
    fontSize: '1.25rem',
    fontWeight: 700,
    marginBottom: '0.375rem',
  },
  statusBadge: {
    display: 'inline-block',
    color: '#fff',
    fontSize: '0.75rem',
    fontWeight: 600,
    padding: '0.25rem 0.625rem',
    borderRadius: '999px',
  },
  logoutBtn: {
    width: 'auto',
    minHeight: 'auto',
    padding: '0.5rem 1rem',
    fontSize: '0.875rem',
  },
  sectionTitle: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#374151',
    marginBottom: '0.75rem',
  },
  statusGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '0.5rem',
  },
  statusBtn: {
    padding: '0.75rem',
    borderRadius: '0.5rem',
    border: 'none',
    fontSize: '0.875rem',
    fontWeight: 600,
    cursor: 'pointer',
  },
  summaryGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: '0.5rem',
  },
  summaryCard: {
    padding: '0.75rem 0.5rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
    textAlign: 'center',
  },
  summaryValue: {
    fontSize: '1.5rem',
    fontWeight: 700,
    color: '#111827',
  },
  summaryLabel: {
    fontSize: '0.75rem',
    color: '#6b7280',
    marginTop: '0.25rem',
  },
  emptyText: {
    fontSize: '0.875rem',
    color: '#9ca3af',
    textAlign: 'center',
    padding: '2rem 0',
  },
  waitingList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  waitingCard: {
    padding: '1rem',
    borderRadius: '0.75rem',
    border: '1px solid #e2e8f0',
    backgroundColor: '#fff',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  waitingInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    flexWrap: 'wrap',
  },
  waitingNumber: {
    fontWeight: 700,
    fontSize: '1rem',
    color: '#3b82f6',
  },
  waitingName: {
    fontWeight: 600,
    fontSize: '0.9rem',
  },
  waitingMeta: {
    fontSize: '0.8rem',
    color: '#6b7280',
  },
  calledBadge: {
    backgroundColor: '#fef3c7',
    color: '#d97706',
    fontSize: '0.75rem',
    fontWeight: 600,
    padding: '0.125rem 0.5rem',
    borderRadius: '999px',
  },
  actionButtons: {
    display: 'flex',
    gap: '0.5rem',
  },
  actionBtn: {
    flex: 1,
    padding: '0.5rem',
    borderRadius: '0.5rem',
    border: 'none',
    fontSize: '0.875rem',
    fontWeight: 600,
    cursor: 'pointer',
  },
  callBtn: {
    backgroundColor: '#dbeafe',
    color: '#1d4ed8',
  },
  enterBtn: {
    backgroundColor: '#dcfce7',
    color: '#16a34a',
  },
  noshowBtn: {
    backgroundColor: '#fee2e2',
    color: '#dc2626',
  },
  overlay: {
    position: 'fixed',
    inset: 0,
    backgroundColor: 'rgba(0,0,0,0.4)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '1.5rem',
    zIndex: 100,
  },
  dialog: {
    backgroundColor: '#fff',
    borderRadius: '1rem',
    padding: '1.5rem',
    width: '100%',
    maxWidth: 360,
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  dialogMessage: {
    fontSize: '0.9375rem',
    fontWeight: 500,
    lineHeight: 1.6,
  },
  dialogButtons: {
    display: 'flex',
    gap: '0.75rem',
  },
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
    textAlign: 'center',
  },
  alertBanner: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0.875rem 1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#fef3c7',
    border: '1px solid #fcd34d',
    fontSize: '0.875rem',
    fontWeight: 500,
    color: '#92400e',
  },
  alertClose: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem',
    color: '#92400e',
    padding: '0 0.25rem',
  },
  notifDeniedBanner: {
    padding: '0.75rem 1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f3f4f6',
    fontSize: '0.8rem',
    color: '#6b7280',
  },
}

export default DashboardPage
