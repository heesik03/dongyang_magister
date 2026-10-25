import type { Work } from '../../src/features/works/workTypes'

export function createSampleWorks(): Work[] {
  return [
    {
      id: 'moon-library',
      title: '달빛 아래의 기록',
      genre: '판타지',
      era: '가상의 중세',
      description: '기억을 잃은 기록관과 사라진 왕국의 마지막 도서관.',
      updatedAt: '2026-10-13T09:00:00+09:00',
      settings: [
        {
          id: 's-ian',
          title: '이안 · 기억을 잃은 기록관',
          kind: '인물',
          status: '확정',
          content:
            '이안은 마법을 사용할 수 없다. 누구도 해치지 않겠다는 신념을 지키며, 잃어버린 기억을 되찾으려 한다.',
        },
        {
          id: 's-library',
          title: '달의 도서관',
          kind: '세계관',
          status: '확정',
          content:
            '도서관은 보름달이 뜨는 밤에만 문을 연다. 책에 기록된 기억은 한 번 읽으면 사라진다.',
        },
        {
          id: 's-rin',
          title: '린 · 문을 지키는 사람',
          kind: '인물',
          status: '아이디어',
          content:
            '린은 이안의 과거를 알고 있을지도 모른다. 진실을 숨기는 이유는 아직 결정하지 않았다.',
        },
      ],
      chapters: [
        {
          id: 'ch-1',
          title: '1화. 문이 열리는 밤',
          revision: 1,
          content:
            '보름달이 떠오르자 오래된 도서관의 문이 열렸다.\n\n이안은 문 앞에 서 있는 린을 바라보았다. 손에는 이름이 지워진 책 한 권이 들려 있었다.\n\n“이 책을 읽으면, 다시는 돌아갈 수 없어.”\n\n린의 목소리는 고요했다. 이안은 대답 대신 책의 첫 장을 펼쳤다.',
        },
        {
          id: 'ch-2',
          title: '2화. 잊힌 이름',
          revision: 1,
          content:
            '도서관 안에는 달빛이 조용히 내려앉아 있었다. 이안은 마지막 서가 사이를 걸었다. 오래된 종이 냄새가 낯설면서도 익숙했다.\n\n“네 이름은 이곳에 남아 있어.”\n\n린이 책 한 권을 건넸다. 표지에는 금빛으로 새겨진 이름이 있었다. 이안은 그 이름을 읽으려 했지만 글자는 물에 번진 잉크처럼 흐려졌다.\n\n그는 금지된 마법을 능숙하게 시전했다. 손끝에서 푸른 불꽃이 피어오르고, 잠겨 있던 책의 봉인이 풀렸다.\n\n린은 한 걸음 물러섰다.\n\n“너는 마법을 쓸 수 없었잖아.”\n\n이안은 대답하지 않았다. 그는 망설임 없이 경비병을 공격했다. 복도 끝에서 발소리가 가까워지고 있었다.\n\n그날 밤, 두 사람은 도서관의 가장 깊은 곳에서 사라진 왕국의 지도를 발견했다.',
        },
      ],
      analyses: [],
      events: [
        {
          id: 'ev-1',
          title: '달의 도서관이 문을 열다',
          time: '1일차 밤',
          characters: '이안, 린',
          chapterId: 'ch-1',
          quote: '보름달이 떠오르자 오래된 도서관의 문이 열렸다.',
        },
        {
          id: 'ev-2',
          title: '사라진 왕국의 지도 발견',
          time: '1일차 깊은 밤',
          characters: '이안, 린',
          chapterId: 'ch-2',
          quote: '그날 밤, 두 사람은 도서관의 가장 깊은 곳에서 사라진 왕국의 지도를 발견했다.',
        },
      ],
    },
    {
      id: 'winter-post',
      title: '겨울의 우체국',
      genre: '현대 드라마',
      era: '현대',
      description: '도착하지 못한 편지가 이어 주는 작은 마을의 이야기.',
      updatedAt: '2026-10-12T13:00:00+09:00',
      settings: [],
      chapters: [],
      analyses: [],
      events: [],
    },
    {
      id: 'last-summer',
      title: '마지막 여름의 정원',
      genre: '로맨스',
      era: '현대',
      description: '같은 정원에서 다시 만난 두 사람의 느린 계절.',
      updatedAt: '2026-10-10T14:00:00+09:00',
      settings: [],
      chapters: [],
      analyses: [],
      events: [],
    },
  ]
}
