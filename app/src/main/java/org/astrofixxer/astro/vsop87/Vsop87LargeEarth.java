package org.astrofixxer.astro.vsop87;

/** Public access to the package-private "large" Earth and Earth-Moon barycentre series the web app uses. */
public final class Vsop87LargeEarth {
    private Vsop87LargeEarth() {}

    public static double[] getEarth(double t) {
        return new double[] {vsop87a_large_earth.earth_x(t), vsop87a_large_earth.earth_y(t), vsop87a_large_earth.earth_z(t)};
    }

    public static double[] getEmb(double t) {
        return new double[] {vsop87a_large_emb.emb_x(t), vsop87a_large_emb.emb_y(t), vsop87a_large_emb.emb_z(t)};
    }
}
