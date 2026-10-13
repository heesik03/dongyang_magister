import { expect, test } from '@playwright/test'

test('보호 라우트 → 시연 로그인 → AI 분석 → 작가 판단 → 타임라인', async ({ page }) => {
  await page.goto('/works/moon-library/manuscript')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: '샘플 작품으로 둘러보기' }).click()
  await expect(page).toHaveURL(/\/works\/moon-library\/manuscript$/)
  await page.getByRole('button', { name: 'AI 시연 분석', exact: true }).click()
  await expect(page.getByRole('button', { name: /설정 충돌 후보 미검토/ })).toBeVisible()
  await page.getByRole('button', { name: '의도된 설정', exact: true }).click()
  await page.getByLabel('의도와 적용 이유').fill('2화에서 봉인이 풀리는 반전입니다.')
  await page.getByRole('button', { name: '판단 저장', exact: true }).click()
  await expect(page.getByRole('button', { name: /설정 충돌 후보 의도된 설정/ })).toBeVisible()
  await page.getByRole('link', { name: '사건 타임라인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '달의 도서관이 문을 열다' })).toBeVisible()
})
