"""Server-side blue/red visibility prerequisites and lifecycle regressions."""
import copy
import unittest
from verify_all import World, Technique, C


class BlueRedLifecycleTests(unittest.TestCase):
    def make(self,kind):
        return Technique('p','player',0,kind,[0,2,3],[0,0,1])

    def world(self):
        w=World();w.packets=[]
        w.sync=lambda:w.packets.append((w.tick,copy.deepcopy(w.tech.snapshot())))
        return w

    def test_blue_and_red_start_are_present_in_first_server_snapshot(self):
        for kind,name in (('blue',C.BLUE),('red',C.RED)):
            w=self.world();w.carried=name
            self.assertTrue(w.tech.use('player',name))
            state=w.packets[-1][1]['techniques'][0]
            self.assertEqual((state['kind'],state['phase'],state['age']),(kind,'hold',0))
            self.assertGreater(state['pos'][2],0)
            self.assertGreater(state['radius'],0)

    def test_expired_blue_cannot_restart_on_a_late_release(self):
        p=self.make('blue')
        for _ in range(300):p.advance([0,2,0],[0,0,1])
        self.assertEqual(p.phase,'end')
        before=p.snapshot()
        self.assertFalse(p.release())
        self.assertEqual(p.snapshot(),before)
        for _ in range(15):p.advance()
        self.assertEqual(p.phase,'done')
        before=p.snapshot();self.assertFalse(p.release());p.advance()
        self.assertEqual(p.snapshot(),before)

    def test_consumed_blue_cannot_reappear_after_unlimited_merge(self):
        p=self.make('blue');p.consume=True;p.switch('end')
        before=p.snapshot();self.assertFalse(p.release())
        self.assertEqual(before,p.snapshot())

    def test_same_phase_transition_is_not_an_animation_restart(self):
        p=self.make('red');p.advance([0,2,0],[0,0,1]);before=p.snapshot()
        self.assertFalse(p.switch('hold'))
        self.assertEqual(before,p.snapshot())

    def test_red_autocharges_and_beams_are_synced_on_transition_tick(self):
        w=self.world();w.tick=1;w.carried=C.RED;w.tech.use('player',C.RED)
        w.step(C.RED_CHARGE_TICKS)
        self.assertNotEqual(w.tick%3,0)
        stamp,data=w.packets[-1]
        self.assertEqual(stamp,w.tick)
        self.assertEqual((data['techniques'][0]['phase'],data['techniques'][0]['age']),('windup',0))
        w.step(6)
        stamp,data=w.packets[-1]
        self.assertEqual(stamp,w.tick)
        self.assertEqual((data['techniques'][0]['phase'],data['techniques'][0]['age']),('beam',0))

    def test_short_red_release_is_synced_without_waiting_for_tick(self):
        w=self.world();w.carried=C.RED;w.tech.use('player',C.RED);w.step(5)
        w.tech.release('player',C.RED)
        stamp,data=w.packets[-1]
        self.assertEqual(stamp,w.tick)
        self.assertEqual((data['techniques'][0]['phase'],data['techniques'][0]['age']),('flight',0))

    def test_expiration_sends_a_removal_snapshot(self):
        w=self.world();w.carried=C.BLUE;w.tech.use('player',C.BLUE)
        w.step(315)
        stamp,data=w.packets[-1]
        self.assertEqual(stamp,w.tick)
        self.assertEqual(data['techniques'],[])

    def test_caster_disappearance_sends_a_removal_snapshot(self):
        w=self.world();w.carried=C.BLUE;w.tech.use('player',C.BLUE)
        w.entities['player']['health']=0;w.step()
        self.assertEqual(w.packets[-1][1]['techniques'],[])

    def test_two_released_blues_keep_independent_ids(self):
        w=self.world();w.carried=C.BLUE;w.tech.use('player',C.BLUE)
        first=list(w.tech.active)[0];w.tech.release('player',C.BLUE)
        self.assertTrue(w.tech.use('player',C.BLUE))
        records=w.packets[-1][1]['techniques']
        self.assertEqual(len(records),2)
        self.assertEqual(len(set(p['id'] for p in records)),2)
        self.assertEqual(next(p for p in records if p['id']==first)['phase'],'rest')

    def test_merge_immediately_hides_both_and_announces_burst(self):
        w=self.world();w.carried=C.BLUE;w.tech.use('player',C.BLUE)
        blue=list(w.tech.active.values())[0];w.tech.release('player',C.BLUE)
        w.carried=C.RED;w.tech.use('player',C.RED)
        red=next(p for p in w.tech.active.values() if p.kind=='red')
        red.pos=blue.pos[:];w.tech.release('player',C.RED);w.step()
        stamp,data=w.packets[-1]
        self.assertEqual(stamp,w.tick)
        self.assertEqual(len(data['bursts']),1)
        self.assertTrue(all(p['consume'] for p in data['techniques']))


if __name__=='__main__':
    unittest.main(verbosity=2)
