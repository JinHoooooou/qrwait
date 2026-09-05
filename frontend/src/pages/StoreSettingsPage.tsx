import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import {getStoreSettings, updateStoreSettings} from '../api/owner'

function StoreSettingsPage() {
  const navigate = useNavigate()

  const [tableCount, setTableCount] = useState(5)
  const [avgTurnoverMinutes, setAvgTurnoverMinutes] = useState(30)
  const [openTime, setOpenTime] = useState('09:00')
  const [closeTime, setCloseTime] = useState('22:00')
  const [alertThreshold, setAlertThreshold] = useState(10)
  const [alertEnabled, setAlertEnabled] = useState(true)
  const [callGraceMinutes, setCallGraceMinutes] = useState(5)
  const [formulaExample, setFormulaExample] = useState('')

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getStoreSettings()
      .then((data) => {
        setTableCount(data.tableCount)
        setAvgTurnoverMinutes(data.avgTurnoverMinutes)
        setOpenTime(data.openTime)
        setCloseTime(data.closeTime ?? '22:00')
        setAlertThreshold(data.alertThreshold)
        setAlertEnabled(data.alertEnabled)
        setCallGraceMinutes(data.callGraceMinutes)
        setFormulaExample(data.estimatedWaitFormulaExample)
      })
      .catch(() => setError('설정을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

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
        callGraceMinutes,
      })
      navigate('/owner/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장에 실패했습니다.')
      setSaving(false)
    }
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
              onChange={(e) => setOpenTime(e.target.value ? e.target.value : openTime)}
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
        <small style={styles.hint}>시작 시간을 기준으로 대기번호가 1번부터 다시 시작합니다. 새벽 영업분은 전날로 집계됩니다.</small>
      </section>

      {/* 알림 설정 */}
      <section style={styles.section}>
        <p style={styles.sectionTitle}>알림 설정</p>

        <label style={styles.label}>
          혼잡 알림 기준 (팀)
          <input
            style={styles.input}
            type="number"
            min={1}
            max={50}
            value={alertThreshold}
            onChange={(e) => setAlertThreshold(Number(e.target.value))}
          />
          <small style={styles.hint}>대기 팀이 이 수 이상이 되면 알려드려요.</small>
        </label>

        <label style={styles.toggleLabel}>
          <span>혼잡 알림 받기</span>
          <input
            type="checkbox"
            checked={alertEnabled}
            onChange={(e) => setAlertEnabled(e.target.checked)}
            style={styles.checkbox}
          />
        </label>
      </section>

      {/* 호출 설정 */}
      <section style={styles.section}>
        <p style={styles.sectionTitle}>호출 설정</p>

        <label style={styles.label}>
          호출 유예 시간 (분)
          <input
            style={styles.input}
            type="number"
            min={0}
            max={60}
            value={callGraceMinutes}
            onChange={(e) => setCallGraceMinutes(Number(e.target.value))}
          />
          <small style={styles.hint}>호출 후 이 시간이 지나면 대기 목록에서 강조 표시됩니다. 자동으로 취소되지는 않습니다.</small>
        </label>
      </section>

      <Button onClick={handleSave} disabled={saving}>
        {saving ? '저장 중...' : '저장'}
      </Button>
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
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
  },
}

export default StoreSettingsPage
