import { httpClient, clearCsrfToken } from '../../shared/httpClient'
import { readInteger, readRecord, readString } from '../../shared/responseReader'
import type { User } from '../auth/authTypes'
import { useWorksStore } from '../works/useWorksStore'

export interface RecentManuscriptSummary {
  workId: string
  workTitle: string
  chapterId: string
  chapterNo: number
  chapterTitle: string
  updatedAt: string
}

export interface UserActivity {
  workCount: number
  chapterCount: number
  unreviewedCount: number
  recentManuscript: RecentManuscriptSummary | null
}

function parseUser(raw: unknown): User {
  const record = readRecord(raw)
  return {
    id: readInteger(record, 'id', 1),
    name: readString(record, 'name'),
    email: readString(record, 'email'),
  }
}

function parseActivity(raw: unknown): UserActivity {
  const record = readRecord(raw)
  let recentManuscript: RecentManuscriptSummary | null = null
  if (record.recentManuscript !== null && record.recentManuscript !== undefined) {
    const recent = readRecord(record.recentManuscript)
    recentManuscript = {
      workId: String(readInteger(recent, 'workId', 1)),
      workTitle: readString(recent, 'workTitle'),
      chapterId: readString(recent, 'chapterId'),
      chapterNo: readInteger(recent, 'chapterNo', 1),
      chapterTitle: readString(recent, 'chapterTitle'),
      updatedAt: readString(recent, 'updatedAt'),
    }
  }
  return {
    workCount: readInteger(record, 'workCount', 0),
    chapterCount: readInteger(record, 'chapterCount', 0),
    unreviewedCount: readInteger(record, 'unreviewedCount', 0),
    recentManuscript,
  }
}

export const ACCOUNT_WITHDRAWN_STORAGE_PREFIX = 'magister.account-withdrawn:'

/** 서버 탈퇴 성공 후 열려 있는 같은 출처의 탭에 정리 신호를 전달한다. */
export function notifyAccountWithdrawal(email: string): void {
  try {
    const key = ACCOUNT_WITHDRAWN_STORAGE_PREFIX + email
    localStorage.setItem(key, crypto.randomUUID())
    localStorage.removeItem(key)
  } catch {
    // 저장소에 접근할 수 없어도 현재 탭의 탈퇴 처리는 계속한다.
  }
}

/** 이 탭의 해당 계정 초안과 복구 자료를 정리한다. */
export function purgeAccountBrowserStorage(email: string): void {
  if (useWorksStore.getState().account === email) useWorksStore.getState().reset()
  try {
    localStorage.removeItem('magister.works.v1:' + email)
    localStorage.removeItem('magister.drafts.v1:' + email)
    localStorage.removeItem('magister.work-deleted:' + email)
    localStorage.removeItem('magister.chapter-saved:' + email)
    localStorage.removeItem('magister.workspace-saved:' + email)
  } catch {
    // 세션 저장소는 로컬 저장소와 별개로 정리를 시도한다.
  }
  try {
    const recoveryPrefix = 'magister.recovery:' + email + ':'
    const decisionPrefix = 'magister.decision-draft.v1:' + encodeURIComponent(email) + ':'
    const sessionKeys = Array.from({ length: sessionStorage.length }, (_, index) =>
      sessionStorage.key(index),
    )
    for (const key of sessionKeys) {
      if (key && (key.startsWith(recoveryPrefix) || key.startsWith(decisionPrefix))) {
        sessionStorage.removeItem(key)
      }
    }
  } catch {
    // 브라우저 저장소가 비활성화되었거나 접근이 차단된 환경에서도 세션 만료 처리는 계속 진행한다.
  }
}

export const accountClient = {
  async getActivity(signal?: AbortSignal): Promise<UserActivity> {
    return parseActivity(await httpClient.get('/user/me/activity', { signal }))
  },
  async updateProfile(name: string): Promise<User> {
    return parseUser(await httpClient.patch('/user/me/profile', { name }))
  },
  async changePassword(input: {
    currentPassword: string
    newPassword: string
    confirmPassword: string
  }): Promise<void> {
    await httpClient.post('/user/me/password', input, {
      expireSessionOnUnauthorized: false,
    })
    clearCsrfToken()
  },
  async withdraw(input: { currentPassword: string; agreed: boolean }): Promise<void> {
    await httpClient.post('/user/me/withdraw', input, {
      expireSessionOnUnauthorized: false,
    })
    clearCsrfToken()
  },
}
