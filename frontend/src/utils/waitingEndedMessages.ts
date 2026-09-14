import type {WaitingTerminalStatus} from '../api/waiting'

// waitingId 자체가 없는 경우(진짜 404)는 종료 상태가 아니라 별도로 다룬다.
export type WaitingEndedReason = WaitingTerminalStatus | 'NOT_FOUND'

interface EndedMessage {
  title: string
  desc: string
}

export const WAITING_ENDED_MESSAGES: Record<WaitingEndedReason, EndedMessage> = {
  ENTERED: {title: '입장 완료!', desc: '맛있게 드세요 🎉'},
  NO_SHOW: {title: '노쇼로 처리되었습니다', desc: '호출에 응답하지 않아 웨이팅이 종료되었습니다.'},
  CANCELLED: {title: '웨이팅이 취소되었습니다', desc: '다시 대기하시려면 QR을 스캔해 등록해 주세요.'},
  NOT_FOUND: {title: '웨이팅 정보를 찾을 수 없습니다', desc: '잘못된 접근이거나 만료된 링크입니다.'},
}
