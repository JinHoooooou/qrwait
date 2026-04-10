import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import {getStoreSettings, updateStoreSettings} from '../api/owner'
import {getStoreQrUrl} from '../api/waiting'
import useOwnerStore from '../store/ownerStore'

function StoreSettingsPage() {
  const navigate = useNavigate()
  const storeId = useOwnerStore((s) => s.storeId)

  const [tableCount, setTableCount] = useState(5)
  const [avgTurnoverMinutes, setAvgTurnoverMinutes] = useState(30)
  const [openTime, setOpenTime] = useState('09:00')
  const [closeTime, setCloseTime] = useState('22:00')
  const [alertThreshold, setAlertThreshold] = useState(10)
  const [alertEnabled, setAlertEnabled] = useState(true)
  const [formulaExample, setFormulaExample] = useState('')

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState(false)

  const qrUrl = storeId ? getStoreQrUrl(storeId) : null

  useEffect(() => {
    getStoreSettings()
      .then((data) => {
        setTableCount(data.tableCount)
        setAvgTurnoverMinutes(data.avgTurnoverMinutes)
        setOpenTime(data.openTime ?? '09:00')
        setCloseTime(data.closeTime ?? '22:00')
        setAlertThreshold(data.alertThreshold)
        setAlertEnabled(data.alertEnabled)
        setFormulaExample(data.estimatedWaitFormulaExample)
      })
      .catch(() => setError('설정을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  const showToast = () => {
    setToast(true)
    setTimeout(() => setToast(false), 3000)
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    try {
      await updateStoreSettings({
        tableCount,
        avgTurnoverMinutes,
        openTime,
        closeTime,
        alertThreshold,
        alertEnabled,
      })
      showToast()
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const handleDownloadQr = async () => {
    if (!qrUrl) return
    const res = await fetch(qrUrl)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'qr-code.png'
    a.click()
    URL.revokeObjectURL(url)
  }

  if (loading) return null

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>매장 설정</h1>
        <Button variant="secondary" onClick={() => navigate('/owner/dashboard')} style={styles.backBtn}>
          대시보드
        </Button>
      </div>

      {error && <p style={styles.error}>{error}</p>}

      {/* 대기 시간 설정 */}
      <section style={styles.section}>
        <p style={styles.sectionTitle}>대기 시간 설정</p>

        <label style={styles.label}>
          테이블 수
          <input
            style={styles.input}
            type="number"
            min={1}
            max={100}
            value={tableCount}
            onChange={(e) => setTableCount(Number(e.target.value))}
          />
        </label>

        <label style={styles.label}>
          팀당 평균 이용시간 (분)
          <input
            style={styles.input}
            type="number"
            min={5}
            max={300}
            value={avgTurnoverMinutes}
            onChange={(e) => setAvgTurnoverMinutes(Number(e.target.value))}
          />
        </label>

        {formulaExample && (
          <p style={styles.hint}>예상 대기시간 계산식: {formulaExample}</p>
        )}
      </section>

      {/* 영업 시간 설정 */}
      <section style={styles.section}>
        <p style={styles.sectionTitle}>영업 시간</p>
        <div style={styles.timeRow}>
          <label style={{...styles.label, flex: 1}}>
            시작 시간
            <input
              style={styles.input}
              type="time"
              value={openTime}
              onChange={(e) => setOpenTime(e.target.value)}
            />
          </label>
          <label style={{...styles.label, flex: 1}}>
            종료 시간
            <input
              style={styles.input}
              type="time"
              value={closeTime}
              onChange={(e) => setCloseTime(e.target.value)}
            />
          </label>
        </div>
      </section>

      {/* 알림 설정 */}
      <section style={styles.section}>
        <p style={styles.sectionTitle}>알림 설정</p>

        <label style={styles.label}>
          대기자 알림 임계값 (팀)
          <input
            style={styles.input}
            type="number"
            min={1}
            max={50}
            value={alertThreshold}
            onChange={(e) => setAlertThreshold(Number(e.target.value))}
          />
        </label>

        <label style={styles.toggleLabel}>
          <span>대기자 수 초과 알림</span>
          <input
            type="checkbox"
            checked={alertEnabled}
            onChange={(e) => setAlertEnabled(e.target.checked)}
            style={styles.checkbox}
          />
        </label>
      </section>

      {/* QR 코드 */}
      {qrUrl && (
        <section style={styles.section}>
          <p style={styles.sectionTitle}>QR 코드</p>
          <div style={styles.qrCard}>
            <img src={qrUrl} alt="QR 코드" style={styles.qrImage} />
          </div>
          <Button variant="secondary" onClick={handleDownloadQr}>
            PNG 다운로드
          </Button>
        </section>
      )}

      <Button onClick={handleSave} disabled={saving}>
        {saving ? '저장 중...' : '저장'}
      </Button>

      {/* 토스트 알림 */}
      {toast && (
        <div style={styles.toast}>설정이 저장되었습니다.</div>
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
    alignItems: 'center',
  },
  title: {
    fontSize: '1.25rem',
    fontWeight: 700,
  },
  backBtn: {
    width: 'auto',
    minHeight: 'auto',
    padding: '0.5rem 1rem',
    fontSize: '0.875rem',
  },
  section: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
    padding: '1.25rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  sectionTitle: {
    fontSize: '0.875rem',
    fontWeight: 700,
    color: '#374151',
  },
  label: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    fontWeight: 600,
    fontSize: '0.875rem',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
    fontWeight: 400,
    backgroundColor: '#fff',
  },
  timeRow: {
    display: 'flex',
    gap: '0.75rem',
  },
  toggleLabel: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    fontWeight: 600,
    fontSize: '0.875rem',
    padding: '0.75rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    backgroundColor: '#fff',
  },
  checkbox: {
    width: 20,
    height: 20,
    cursor: 'pointer',
  },
  hint: {
    fontSize: '0.8rem',
    color: '#6b7280',
  },
  qrCard: {
    display: 'flex',
    justifyContent: 'center',
    padding: '1.5rem',
    borderRadius: '0.75rem',
    backgroundColor: '#fff',
    border: '1px solid #e2e8f0',
  },
  qrImage: {
    width: 180,
    height: 180,
  },
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
  },
  toast: {
    position: 'fixed',
    bottom: '2rem',
    left: '50%',
    transform: 'translateX(-50%)',
    backgroundColor: '#111827',
    color: '#fff',
    padding: '0.75rem 1.5rem',
    borderRadius: '999px',
    fontSize: '0.875rem',
    fontWeight: 500,
    zIndex: 200,
    whiteSpace: 'nowrap',
  },
}

export default StoreSettingsPage
