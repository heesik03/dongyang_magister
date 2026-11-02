import bookEmpty from '../assets/figma/44019.svg'
import bookBrand from '../assets/figma/f5cbb.svg'
import book from '../assets/figma/2032e.svg'
import grid from '../assets/figma/2f502.svg'
import search from '../assets/figma/60064.svg'
import file from '../assets/figma/df104.svg'
import clock from '../assets/figma/0a190.svg'
import check from '../assets/figma/896f4.svg'
import chevron from '../assets/figma/117e3.svg'
export type IconName =
  | 'book-empty'
  | 'book'
  | 'grid'
  | 'plus'
  | 'arrow'
  | 'search'
  | 'file'
  | 'spark'
  | 'clock'
  | 'check'
  | 'logout'
  | 'menu'
  | 'close'
  | 'settings'
  | 'chevron'
  | 'save'
  | 'history'
  | 'eye'
  | 'eye-off'
  | 'edit'
  | 'refresh'
  | 'download'
  | 'trash'
  | 'gear'
  | 'cloud-check'
  | 'cloud-upload'
  | 'cloud-alert'
  | 'panel-left-close'
  | 'panel-left-open'
const paths: Record<IconName, string> = {
  save: 'M5 3h12l4 4v14H3V3h2Zm2 0v6h10V3M7 21v-8h10v8',
  history: 'M3 11a9 9 0 1 1 2.5 7M3 4v7h7m2-4v6l4 2',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  edit: 'm15 4 5 5M4 20l4-1L21 6l-4-4L4 15v5Z',
  refresh: 'M20 7a9 9 0 0 0-15-1L2 9m0-6v6h6m-4 8a9 9 0 0 0 15 1l3-3m0 6v-6h-6',
  download: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
  trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7',
  gear: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm8 4c0-.6-.1-1.2-.2-1.7l2-1.5-2-3.4-2.3.9a8 8 0 0 0-3-1.7L14 2h-4l-.5 2.6a8 8 0 0 0-3 1.7l-2.3-.9-2 3.4 2 1.5a8 8 0 0 0 0 3.4l-2 1.5 2 3.4 2.3-.9a8 8 0 0 0 3 1.7L10 22h4l.5-2.6a8 8 0 0 0 3-1.7l2.3.9 2-3.4-2-1.5c.1-.5.2-1.1.2-1.7Z',
  'eye-off':
    'M3 3l18 18M10.6 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.1 4M6.2 6.2A20 20 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.8-1.5M10 10a3 3 0 0 0 4 4',
  'cloud-check': 'M8 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M10 18l3 3 7-7',
  'cloud-upload':
    'M7 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M12 21V12m-3 3 3-3 3 3',
  'cloud-alert': 'M7 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M13 12v4m0 4v.01',
  'panel-left-close': 'M3 3h18v18H3V3Zm5 0v18m8-13-4 4 4 4',
  'panel-left-open': 'M3 3h18v18H3V3Zm5 0v18m4-13 4 4-4 4',
  'book-empty': '',
  chevron: 'm9 6 6 6-6 6',
  book: 'M3 4h6a3 3 0 0 1 3 3v14a4 4 0 0 0-4-3H3V4Zm18 0h-6a3 3 0 0 0-3 3v14a4 4 0 0 1 4-3h5V4Z',
  grid: 'M3 3h7v7H3V3Zm11 0h7v7h-7V3ZM3 14h7v7H3v-7Zm11 0h7v7h-7v-7Z',
  plus: 'M12 5v14M5 12h14',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  file: 'M14 2H5v20h14V7l-5-5Zm0 0v6h5M8 12h8M8 16h8',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  clock: 'M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  check: 'm5 12 4 4L19 6',
  logout: 'M9 3H3v18h6m5-14 5 5-5 5M8 12h11',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'm6 6 12 12M6 18 18 6',
  settings: 'M4 7h16M4 17h16M8 4v6M16 14v6',
}
const assets = { book, grid, search, file, clock, check, chevron, 'book-empty': bookEmpty }
export function Icon({ name, brand = false }: { name: IconName; brand?: boolean }) {
  const asset =
    name === 'book' && brand
      ? bookBrand
      : name in assets
        ? assets[name as keyof typeof assets]
        : undefined
  if (asset) return <img className="design-icon" src={asset} alt="" aria-hidden="true" />
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  )
}
