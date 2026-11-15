package com.optem.logic;

import com.optem.database.MySQL;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Detecta MySQL real, no sólo Internet. Nunca ejecuta JDBC en el hilo JavaFX. */
public final class OfflineSyncService {
    public enum Status { COMPROBANDO, CONECTADO, SIN_CONEXION }
    private static final OfflineSyncService INSTANCE=new OfflineSyncService();
    public static OfflineSyncService get(){return INSTANCE;}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"optem-offline-sync");t.setDaemon(true);return t;});
    private final AtomicBoolean started=new AtomicBoolean();
    private volatile Status status=Status.COMPROBANDO;
    private OfflineSyncService(){}
    public Status status(){return status;}
    public int pending(){return OfflineStore.pendingCount();}
    public void start(){ if(started.compareAndSet(false,true))worker.scheduleWithFixedDelay(this::poll,0,10,TimeUnit.SECONDS); }
    public void checkSoon(){worker.execute(this::poll);}
    public static boolean connectionFailure(SQLException ex){
        for(Throwable t=ex;t!=null;t=t.getCause()){
            if(t instanceof SQLTransientConnectionException || t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) return true;
            if(t instanceof SQLException sql && sql.getSQLState()!=null && sql.getSQLState().startsWith("08"))return true;
        }
        return false;
    }
    public void markOffline(){ status=Status.SIN_CONEXION; }
    private void poll(){
        try(Connection c=MySQL.conectar();PreparedStatement st=c.prepareStatement("SELECT 1")){
            st.setQueryTimeout(5);
            try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new SQLException("Sin respuesta de MySQL");}
            status=Status.CONECTADO;
            flush();
        }catch(Exception e){status=Status.SIN_CONEXION;}
    }
    /** Sólo escritura de notas personales. Otras tablas requieren sincronizadores propios. */
    private void flush(){
        for(OfflineStore.Operation op:OfflineStore.pending()){
            try(Connection c=MySQL.conectar()){
                c.setAutoCommit(false);
                try{
                    if("DELETE".equals(op.kind())){
                        try(PreparedStatement st=c.prepareStatement("DELETE FROM OPTEM_NOTAS_PERSONALES WHERE id=? AND correo_usuario=?")){
                            st.setString(1,op.noteId());st.setString(2,op.account());st.executeUpdate();
                        }
                    }else{
                        try(PreparedStatement st=c.prepareStatement("INSERT INTO OPTEM_NOTAS_PERSONALES(id,correo_usuario,titulo,materia,contenido,color_hex) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id")){
                            st.setString(1,op.noteId());st.setString(2,op.account());st.setString(3,op.titulo());st.setString(4,op.materia());st.setString(5,op.contenido());st.setString(6,op.color());st.executeUpdate();
                        }
                    }
                    c.commit();
                    OfflineStore.acknowledge(op);
                }catch(SQLException e){c.rollback();throw e;}
            }catch(SQLException e){
                if(connectionFailure(e)){status=Status.SIN_CONEXION;break;}
                // No descartamos cambios rechazados por SQL ni fingimos éxito.
                System.err.println("OPTEM offline: operación pendiente sin sincronizar: "+e.getMessage());
            }
        }
    }
}
