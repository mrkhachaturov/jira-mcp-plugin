/**
 * Priority Icon Registry
 *
 * To add a new priority icon:
 * 1. Drop the SVG file in this directory (icons/priorities/)
 * 2. Add an import line below
 * 3. Add the mapping: Jira priority name → imported icon
 *
 * The key must match the exact priority name as returned by Jira.
 */

import highest from './highest.svg'
import high from './high.svg'
import medium from './medium.svg'
import low from './low.svg'
import lowest from './lowest.svg'
import blocker from './blocker.svg'
import trivial from './trivial.svg'

export const priorityIcons: Record<string, string> = {
  'Highest': highest,
  'High': high,
  'Medium': medium,
  'Low': low,
  'Lowest': lowest,
  'Blocker': blocker,
  'Minor': trivial,
  'Trivial': trivial,
}
