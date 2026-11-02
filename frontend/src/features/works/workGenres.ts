export const GENRES = ['판타지', '로맨스', '현대 드라마', '역사', '미스터리', 'SF', '기타']

const genreKeys: Record<string, string> = {
  판타지: 'fantasy',
  로맨스: 'romance',
  '현대 드라마': 'drama',
  역사: 'history',
  미스터리: 'mystery',
  SF: 'sf',
}
export const genreClassName = (genre: string) => 'genre-' + (genreKeys[genre] || 'other')
