import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import type {MyStoreResponse} from '../api/owner'
import {getMyStore} from '../api/owner'
import {getStoreQrUrl} from '../api/waiting'
import LoadingSpinner from '../components/LoadingSpinner'

function QrPrintPage() {
  const navigate = useNavigate()
  const [store, setStore] = useState<MyStoreResponse | null>(null)

  useEffect(() => {
    getMyStore().then(setStore)
  }, [])

  if (!store) return <LoadingSpinner/>

  const qrUrl = getStoreQrUrl(store.storeId)

  return (
      <>
        <div className="no-print" style={styles.actions}>
          <button style={styles.backBtn} onClick={() => navigate('/owner/dashboard')}>
            ← 대시보드
          </button>
          <button style={styles.printBtn} onClick={() => window.print()}>
            인쇄 / PDF 저장
          </button>
        </div>

        <div style={styles.printContent}>
          <img src={qrUrl} alt="QR 코드" style={styles.qrImage}/>
          <p style={styles.storeName}>{store.name}</p>
        </div>

        <style>{`
          @media print {
            .no-print { display: none !important; }
            body { margin: 0; }
          }
        `}</style>
      </>
  )
}

const styles: Record<string, React.CSSProperties> = {
  actions: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '1rem 1.5rem',
  },
  backBtn: {
    background: 'none',
    border: 'none',
    color: '#3b82f6',
    cursor: 'pointer',
    fontSize: '0.875rem',
  },
  printBtn: {
    padding: '0.625rem 1.25rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    background: '#fff',
    fontSize: '0.875rem',
    fontWeight: 600,
    cursor: 'pointer',
  },
  printContent: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '80vh',
    gap: '1.5rem',
  },
  qrImage: {
    width: 300,
    height: 300,
  },
  storeName: {
    fontSize: '1.75rem',
    fontWeight: 700,
    textAlign: 'center',
    margin: 0,
  },
}

export default QrPrintPage
