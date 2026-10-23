import { expect, test } from '@playwright/test'

test('회원가입 → 보호 라우트 → 세션 로그인 → AI 분석 → 작가 판단 → 타임라인', async ({
  page,
}, testInfo) => {
  const email = `writer-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`
  await page.goto('/signup')
  await page.getByLabel('필명').fill('테스트 작가')
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill('Password1!')
  await page.getByLabel('비밀번호 확인').fill('Password1!')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '회원가입하고 시작하기' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.goto('/works/moon-library/manuscript')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill('Password1!')
  await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true })
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  const sessionCookie = (await page.context().cookies()).find(
    (cookie) => cookie.name === 'JSESSIONID',
  )
  expect(sessionCookie?.httpOnly).toBe(true)
  expect(sessionCookie?.sameSite).toBe('Lax')
  await expect(page).toHaveURL(/\/works\/moon-library\/manuscript$/)
  await page.reload()
  await expect(page).toHaveURL(/\/works\/moon-library\/manuscript$/)
  await page.getByRole('button', { name: 'AI 시연 분석', exact: true }).click()
  await expect(page.getByRole('button', { name: /설정 충돌 후보 미검토/ })).toBeVisible()
  await page.getByRole('button', { name: '의도된 설정', exact: true }).click()
  await page.getByLabel('의도와 적용 이유').fill('2화에서 봉인이 풀리는 반전입니다.')
  await page.getByRole('button', { name: '판단 저장', exact: true }).click()
  await expect(page.getByRole('button', { name: /설정 충돌 후보 의도된 설정/ })).toBeVisible()
  await page.getByRole('link', { name: '사건 타임라인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '달의 도서관이 문을 열다' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('timeline.png'), fullPage: true })
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.goto('/works/moon-library/manuscript')
  await expect(page).toHaveURL(/\/login$/)
})
